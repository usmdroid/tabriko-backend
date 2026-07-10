package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.common.util.HmacUtil;
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

    @Value("${app.commission-percent:15}")
    private int commissionPercent;

    @Value("${app.payment.callback-secret:}")
    private String callbackSecret;

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

    // Available balance = balance minus funds already reserved by a PENDING withdrawal
    // minus funds still locked in an active order hold. Used to gate new withdrawals so
    // a user cannot request multiple payouts against the same funds before the first is
    // settled (getBalance() alone does not reflect PENDING withdrawals).
    public BigDecimal getAvailableBalance(UUID userId) {
        BigDecimal balance = getBalance(userId);
        BigDecimal pendingWithdraw = walletTxRepo.sumByUserAndStatusAndTypes(
            userId, TransactionStatus.PENDING, List.of(TransactionType.WITHDRAW));
        BigDecimal activeHold = getHold(userId);
        return balance.subtract(pendingWithdraw).subtract(activeHold).max(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
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
        verifyCallbackSignature(req);

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

    // Verifies the provider's HMAC-SHA256 signature over "transactionId:amount:providerRef"
    // using the shared app.payment.callback-secret, so an attacker who knows a transactionId
    // cannot forge a completion callback.
    private void verifyCallbackSignature(WalletCallbackRequest req) {
        if (callbackSecret == null || callbackSecret.isBlank()) {
            throw ApiException.unauthorized("Payment callback is not configured");
        }
        String payload = req.getTransactionId() + ":" + req.getAmount().toPlainString() + ":" + req.getProviderRef();
        String expected = HmacUtil.sha256Hex(payload, callbackSecret);
        if (!HmacUtil.constantTimeEquals(expected, req.getSignature())) {
            throw ApiException.unauthorized("Invalid payment callback signature");
        }
    }

    @Transactional
    public void withdraw(UUID userId, WithdrawRequest req) {
        // Pessimistic lock on user row prevents concurrent double-spend
        User user = userRepo.findByIdForUpdate(userId)
            .orElseThrow(() -> ApiException.notFound("User not found"));

        BigDecimal available = getAvailableBalance(userId);
        if (available.compareTo(req.getAmount()) < 0) {
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
        BigDecimal activePriceSum = orderRepo.sumPriceByCreatorAndStatuses(creatorId, ACTIVE_ORDER_STATUSES);
        BigDecimal pendingPayout = activePriceSum
            .multiply(BigDecimal.valueOf(100 - commissionPercent))
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
