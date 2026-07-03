package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.WalletTransaction;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.domain.enums.TransactionStatus;
import uz.tabriko.domain.enums.TransactionType;
import uz.tabriko.dto.request.TopUpRequest;
import uz.tabriko.dto.request.WalletCallbackRequest;
import uz.tabriko.dto.request.WithdrawRequest;
import uz.tabriko.dto.response.EarningsResponse;
import uz.tabriko.dto.response.PageResponse;
import uz.tabriko.dto.response.TopUpInitResponse;
import uz.tabriko.dto.response.WalletResponse;
import uz.tabriko.infrastructure.payment.PaymentInitResult;
import uz.tabriko.infrastructure.payment.PaymentProvider;
import uz.tabriko.repository.OrderRepository;
import uz.tabriko.repository.UserRepository;
import uz.tabriko.repository.WalletTransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletTransactionRepository walletTxRepo;
    private final UserRepository userRepo;
    private final OrderRepository orderRepo;
    private final PaymentProvider paymentProvider;
    private final UserMapper mapper;

    private static final List<TransactionType> CREDIT_TYPES =
        List.of(TransactionType.TOPUP, TransactionType.DEPOSIT, TransactionType.REFUND, TransactionType.RELEASE);

    private static final List<TransactionType> DEBIT_TYPES =
        List.of(TransactionType.HOLD, TransactionType.COMMISSION, TransactionType.WITHDRAW);

    private static final List<OrderStatus> ACTIVE_ORDER_STATUSES =
        List.of(OrderStatus.PENDING, OrderStatus.IN_PROGRESS, OrderStatus.DELIVERED);

    // Central balance: sum credits minus debits from COMPLETED transactions
    public BigDecimal getBalance(UUID userId) {
        BigDecimal credits = walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, CREDIT_TYPES);
        BigDecimal debits  = walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, DEBIT_TYPES);
        return credits.subtract(debits).max(BigDecimal.ZERO);
    }

    // Hold = HOLD txs linked to still-active orders
    public BigDecimal getHold(UUID userId) {
        return walletTxRepo.computeActiveHold(userId, TransactionType.HOLD, ACTIVE_ORDER_STATUSES);
    }

    public WalletResponse getWallet(UUID userId, int page, int size) {
        WalletResponse r = new WalletResponse();
        r.setBalance(getBalance(userId));
        r.setHold(getHold(userId));
        r.setHistory(PageResponse.of(
            walletTxRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)),
            mapper::toWalletTxResponse
        ));
        return r;
    }

    @Transactional
    public TopUpInitResponse topUp(UUID userId, TopUpRequest req) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> ApiException.notFound("User not found"));

        // Create a PENDING topup transaction; provider will confirm via callback
        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setAmount(req.getAmount());
        tx.setType(TransactionType.TOPUP);
        tx.setStatus(TransactionStatus.PENDING);
        walletTxRepo.save(tx);

        PaymentInitResult result = paymentProvider.initTopUp(userId, req.getAmount(), tx.getId());

        TopUpInitResponse resp = new TopUpInitResponse();
        resp.setWalletTransactionId(tx.getId());
        resp.setPaymentUrl(result.getPaymentUrl());
        resp.setProviderTransactionId(result.getProviderTransactionId());
        resp.setAmount(req.getAmount());
        resp.setProvider(req.getProvider().name());
        return resp;
    }

    @Transactional
    public void handleCallback(WalletCallbackRequest req) {
        // TODO: verify HMAC signature from payment provider before trusting this call
        WalletTransaction tx = walletTxRepo.findById(req.getTransactionId())
            .orElseThrow(() -> ApiException.notFound("Transaction not found"));

        if (tx.getStatus() == TransactionStatus.COMPLETED) {
            return; // idempotent — already processed
        }
        if (tx.getType() != TransactionType.TOPUP && tx.getType() != TransactionType.DEPOSIT) {
            throw ApiException.badRequest("Transaction is not a topup");
        }
        if (tx.getAmount().compareTo(req.getAmount()) != 0) {
            throw ApiException.badRequest("Amount mismatch");
        }

        tx.setStatus(TransactionStatus.COMPLETED);
        walletTxRepo.save(tx);
    }

    @Transactional
    public void withdraw(UUID userId, WithdrawRequest req) {
        // Pessimistic lock on user row prevents concurrent double-spend
        User user = userRepo.findByIdForUpdate(userId)
            .orElseThrow(() -> ApiException.notFound("User not found"));

        BigDecimal balance = getBalance(userId);
        if (balance.compareTo(req.getAmount()) < 0) {
            throw ApiException.badRequest("Insufficient balance");
        }

        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setAmount(req.getAmount());
        tx.setType(TransactionType.WITHDRAW);
        tx.setStatus(TransactionStatus.PENDING);
        walletTxRepo.save(tx);
    }

    // Creator earnings summary
    public EarningsResponse getEarnings(UUID creatorId) {
        BigDecimal totalEarned = walletTxRepo.sumByUserAndTypeAndStatus(
            creatorId, TransactionType.RELEASE, TransactionStatus.COMPLETED);

        // Pending = estimated net payout for active orders (price * (100 - commission) / 100)
        // Commission is 15% by default; use sum of order prices as approximation
        BigDecimal activePriceSum = orderRepo.sumPriceByCreatorAndStatuses(creatorId, ACTIVE_ORDER_STATUSES);
        BigDecimal pendingPayout = activePriceSum
            .multiply(BigDecimal.valueOf(85))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal withdrawn = walletTxRepo.sumByUserAndTypeAndStatus(
            creatorId, TransactionType.WITHDRAW, TransactionStatus.COMPLETED);

        EarningsResponse r = new EarningsResponse();
        r.setTotalEarned(totalEarned);
        r.setPendingPayout(pendingPayout);
        r.setWithdrawn(withdrawn);
        return r;
    }
}
