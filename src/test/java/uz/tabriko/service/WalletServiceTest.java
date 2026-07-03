package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.WalletTransaction;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.domain.enums.TransactionStatus;
import uz.tabriko.domain.enums.TransactionType;
import uz.tabriko.dto.request.WithdrawRequest;
import uz.tabriko.infrastructure.payment.PaymentProvider;
import uz.tabriko.repository.OrderRepository;
import uz.tabriko.repository.UserRepository;
import uz.tabriko.repository.WalletTransactionRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock WalletTransactionRepository walletTxRepo;
    @Mock UserRepository userRepo;
    @Mock OrderRepository orderRepo;
    @Mock PaymentProvider paymentProvider;
    @Mock UserMapper mapper;

    @InjectMocks WalletService walletService;

    private UUID userId;
    private User user;

    private static final List<TransactionType> CREDIT_TYPES =
        List.of(TransactionType.TOPUP, TransactionType.DEPOSIT, TransactionType.REFUND, TransactionType.RELEASE);
    private static final List<TransactionType> DEBIT_TYPES =
        List.of(TransactionType.HOLD, TransactionType.COMMISSION, TransactionType.WITHDRAW);

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
    }

    // --- Balance calculation ---

    @Test
    void getBalance_returnsCreditsMinusDebits() {
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, CREDIT_TYPES))
            .thenReturn(new BigDecimal("200.00"));
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, DEBIT_TYPES))
            .thenReturn(new BigDecimal("100.00"));

        BigDecimal balance = walletService.getBalance(userId);

        assertThat(balance).isEqualByComparingTo("100.00");
    }

    @Test
    void getBalance_neverReturnsNegative() {
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, CREDIT_TYPES))
            .thenReturn(BigDecimal.ZERO);
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, DEBIT_TYPES))
            .thenReturn(new BigDecimal("50.00"));

        BigDecimal balance = walletService.getBalance(userId);

        assertThat(balance).isEqualByComparingTo("0.00");
    }

    // --- Withdraw guard (B2) ---

    @Test
    void withdraw_succeedsWhenBalanceSufficient() {
        when(userRepo.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        // credits 100, debits 30 → balance 70
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, CREDIT_TYPES))
            .thenReturn(new BigDecimal("100.00"));
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, DEBIT_TYPES))
            .thenReturn(new BigDecimal("30.00"));

        WithdrawRequest req = new WithdrawRequest();
        req.setAmount(new BigDecimal("50.00"));

        walletService.withdraw(userId, req);

        ArgumentCaptor<WalletTransaction> cap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTxRepo).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(TransactionType.WITHDRAW);
        assertThat(cap.getValue().getAmount()).isEqualByComparingTo("50.00");
        assertThat(cap.getValue().getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void withdraw_failsWhenBalanceInsufficient() {
        when(userRepo.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, CREDIT_TYPES))
            .thenReturn(new BigDecimal("30.00"));
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, DEBIT_TYPES))
            .thenReturn(new BigDecimal("30.00")); // balance = 0

        WithdrawRequest req = new WithdrawRequest();
        req.setAmount(new BigDecimal("10.00"));

        assertThatThrownBy(() -> walletService.withdraw(userId, req))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Insufficient balance");

        verify(walletTxRepo, never()).save(any());
    }

    @Test
    void withdraw_usedPessimisticLock() {
        when(userRepo.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, CREDIT_TYPES))
            .thenReturn(new BigDecimal("100.00"));
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, DEBIT_TYPES))
            .thenReturn(BigDecimal.ZERO);

        WithdrawRequest req = new WithdrawRequest();
        req.setAmount(new BigDecimal("50.00"));

        walletService.withdraw(userId, req);

        // Verify findByIdForUpdate (pessimistic lock) was used, NOT findById
        verify(userRepo).findByIdForUpdate(userId);
        verify(userRepo, never()).findById(any());
    }

    // --- Callback idempotent ---

    @Test
    void handleCallback_idempotentWhenAlreadyCompleted() {
        WalletTransaction tx = new WalletTransaction();
        tx.setId(1L);
        tx.setType(TransactionType.TOPUP);
        tx.setAmount(new BigDecimal("100.00"));
        tx.setStatus(TransactionStatus.COMPLETED);

        when(walletTxRepo.findById(1L)).thenReturn(Optional.of(tx));

        uz.tabriko.dto.request.WalletCallbackRequest req = new uz.tabriko.dto.request.WalletCallbackRequest();
        req.setTransactionId(1L);
        req.setAmount(new BigDecimal("100.00"));

        walletService.handleCallback(req);

        verify(walletTxRepo, never()).save(any());
    }
}
