package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.common.util.HmacUtil;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.WalletTransaction;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.domain.enums.TransactionStatus;
import uz.tabriko.domain.enums.TransactionType;
import uz.tabriko.dto.request.WalletCallbackRequest;
import uz.tabriko.dto.request.WithdrawRequest;
import uz.tabriko.infrastructure.payment.PaymentProvider;
import uz.tabriko.repository.CreatorProfileRepository;
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
    @Mock CreatorProfileRepository creatorProfileRepo;
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
        ReflectionTestUtils.setField(walletService, "commissionPercent", 15);
        ReflectionTestUtils.setField(walletService, "callbackSecret", "test-callback-secret");

        // Default: creator has payout configured; individual tests can override.
        CreatorProfile cpWithPayout = new CreatorProfile();
        cpWithPayout.setPayoutCard("8600123412341234");
        lenient().when(creatorProfileRepo.findByUserId(userId)).thenReturn(Optional.of(cpWithPayout));
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

    private void stubNoActiveHold() {
        when(walletTxRepo.computeActiveHold(eq(userId), eq(TransactionType.HOLD), anyList()))
            .thenReturn(BigDecimal.ZERO);
    }

    private void stubPendingWithdraw(BigDecimal amount) {
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.PENDING, List.of(TransactionType.WITHDRAW)))
            .thenReturn(amount);
    }

    @Test
    void withdraw_succeedsWhenBalanceSufficient() {
        when(userRepo.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        // credits 100, debits 30 → balance 70
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, CREDIT_TYPES))
            .thenReturn(new BigDecimal("100.00"));
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, DEBIT_TYPES))
            .thenReturn(new BigDecimal("30.00"));
        stubPendingWithdraw(BigDecimal.ZERO);
        stubNoActiveHold();

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
        stubPendingWithdraw(BigDecimal.ZERO);
        stubNoActiveHold();

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
        stubPendingWithdraw(BigDecimal.ZERO);
        stubNoActiveHold();

        WithdrawRequest req = new WithdrawRequest();
        req.setAmount(new BigDecimal("50.00"));

        walletService.withdraw(userId, req);

        // Verify findByIdForUpdate (pessimistic lock) was used, NOT findById
        verify(userRepo).findByIdForUpdate(userId);
        verify(userRepo, never()).findById(any());
    }

    // ===== B1 regression: PENDING withdraw must reserve funds so a second withdraw =====
    // ===== cannot double-spend the same balance before the first is settled =====

    @Test
    void withdraw_secondRequest_rejectedWhilePendingWithdrawReservesFunds() {
        when(userRepo.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        // balance = 100 (credits 100, debits 0)
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, CREDIT_TYPES))
            .thenReturn(new BigDecimal("100.00"));
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, DEBIT_TYPES))
            .thenReturn(BigDecimal.ZERO);
        // A first withdraw of 80 is already PENDING (not yet completed by admin)
        stubPendingWithdraw(new BigDecimal("80.00"));
        stubNoActiveHold();

        // available = 100 - 80 - 0 = 20; requesting another 50 must be rejected
        WithdrawRequest req = new WithdrawRequest();
        req.setAmount(new BigDecimal("50.00"));

        assertThatThrownBy(() -> walletService.withdraw(userId, req))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Insufficient balance");

        verify(walletTxRepo, never()).save(any());
    }

    @Test
    void withdraw_secondRequest_succeedsWhenWithinRemainingAvailable() {
        when(userRepo.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, CREDIT_TYPES))
            .thenReturn(new BigDecimal("100.00"));
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, DEBIT_TYPES))
            .thenReturn(BigDecimal.ZERO);
        stubPendingWithdraw(new BigDecimal("80.00"));
        stubNoActiveHold();

        // available = 100 - 80 - 0 = 20
        WithdrawRequest req = new WithdrawRequest();
        req.setAmount(new BigDecimal("20.00"));

        walletService.withdraw(userId, req);

        verify(walletTxRepo).save(any());
    }

    // ===== getAvailableBalance =====

    @Test
    void getAvailableBalance_subtractsPendingWithdrawAndActiveHold() {
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, CREDIT_TYPES))
            .thenReturn(new BigDecimal("200.00"));
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, DEBIT_TYPES))
            .thenReturn(BigDecimal.ZERO);
        stubPendingWithdraw(new BigDecimal("30.00"));
        when(walletTxRepo.computeActiveHold(eq(userId), eq(TransactionType.HOLD), anyList()))
            .thenReturn(new BigDecimal("40.00"));

        BigDecimal available = walletService.getAvailableBalance(userId);

        // 200 - 30 - 40 = 130
        assertThat(available).isEqualByComparingTo("130.00");
    }

    @Test
    void getAvailableBalance_neverNegative() {
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, CREDIT_TYPES))
            .thenReturn(new BigDecimal("10.00"));
        when(walletTxRepo.sumByUserAndStatusAndTypes(userId, TransactionStatus.COMPLETED, DEBIT_TYPES))
            .thenReturn(BigDecimal.ZERO);
        stubPendingWithdraw(new BigDecimal("50.00"));
        stubNoActiveHold();

        BigDecimal available = walletService.getAvailableBalance(userId);

        assertThat(available).isEqualByComparingTo("0.00");
    }

    // ===== getEarnings — B1: commission percent must not be hardcoded to 85% =====

    @Test
    void getEarnings_usesInjectedCommissionPercent_defaultFifteen() {
        UUID creatorId = UUID.randomUUID();
        when(walletTxRepo.sumByUserAndTypeAndStatus(creatorId, TransactionType.RELEASE, TransactionStatus.COMPLETED))
            .thenReturn(new BigDecimal("500.00"));
        when(orderRepo.sumPriceByCreatorAndStatuses(eq(creatorId), anyList()))
            .thenReturn(new BigDecimal("100.00"));
        when(walletTxRepo.sumByUserAndTypeAndStatus(creatorId, TransactionType.WITHDRAW, TransactionStatus.COMPLETED))
            .thenReturn(new BigDecimal("200.00"));

        var earnings = walletService.getEarnings(creatorId);

        // 100 * (100 - 15) / 100 = 85.00
        assertThat(earnings.getPendingPayout()).isEqualByComparingTo("85.00");
    }

    @Test
    void getEarnings_respectsNonDefaultCommissionPercent() {
        ReflectionTestUtils.setField(walletService, "commissionPercent", 20);

        UUID creatorId = UUID.randomUUID();
        when(walletTxRepo.sumByUserAndTypeAndStatus(creatorId, TransactionType.RELEASE, TransactionStatus.COMPLETED))
            .thenReturn(BigDecimal.ZERO);
        when(orderRepo.sumPriceByCreatorAndStatuses(eq(creatorId), anyList()))
            .thenReturn(new BigDecimal("100.00"));
        when(walletTxRepo.sumByUserAndTypeAndStatus(creatorId, TransactionType.WITHDRAW, TransactionStatus.COMPLETED))
            .thenReturn(BigDecimal.ZERO);

        var earnings = walletService.getEarnings(creatorId);

        // 100 * (100 - 20) / 100 = 80.00
        assertThat(earnings.getPendingPayout()).isEqualByComparingTo("80.00");
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

        WalletCallbackRequest req = signedCallback(1L, new BigDecimal("100.00"), "ref-1");

        walletService.handleCallback(req);

        verify(walletTxRepo, never()).save(any());
    }

    // --- Callback HMAC signature verification ---

    @Test
    void handleCallback_validSignature_marksCompleted() {
        WalletTransaction tx = new WalletTransaction();
        tx.setId(2L);
        tx.setType(TransactionType.TOPUP);
        tx.setAmount(new BigDecimal("50.00"));
        tx.setStatus(TransactionStatus.PENDING);

        when(walletTxRepo.findById(2L)).thenReturn(Optional.of(tx));

        WalletCallbackRequest req = signedCallback(2L, new BigDecimal("50.00"), "ref-2");

        walletService.handleCallback(req);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(walletTxRepo).save(tx);
    }

    @Test
    void handleCallback_invalidSignature_rejectedAndTransactionUnchanged() {
        WalletTransaction tx = new WalletTransaction();
        tx.setId(3L);
        tx.setType(TransactionType.TOPUP);
        tx.setAmount(new BigDecimal("50.00"));
        tx.setStatus(TransactionStatus.PENDING);

        WalletCallbackRequest req = new WalletCallbackRequest();
        req.setTransactionId(3L);
        req.setAmount(new BigDecimal("50.00"));
        req.setProviderRef("ref-3");
        req.setSignature("bogus-signature");

        assertThatThrownBy(() -> walletService.handleCallback(req))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(401));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);
        verify(walletTxRepo, never()).findById(any());
        verify(walletTxRepo, never()).save(any());
    }

    @Test
    void handleCallback_missingSignature_rejected() {
        WalletCallbackRequest req = new WalletCallbackRequest();
        req.setTransactionId(4L);
        req.setAmount(new BigDecimal("50.00"));
        req.setProviderRef("ref-4");
        req.setSignature(null);

        assertThatThrownBy(() -> walletService.handleCallback(req))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(401));

        verify(walletTxRepo, never()).findById(any());
    }

    @Test
    void handleCallback_secretNotConfigured_rejected() {
        ReflectionTestUtils.setField(walletService, "callbackSecret", "");

        WalletCallbackRequest req = signedCallback(5L, new BigDecimal("50.00"), "ref-5");

        assertThatThrownBy(() -> walletService.handleCallback(req))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(401));

        verify(walletTxRepo, never()).findById(any());
    }

    // --- Payout guard ---

    @Test
    void withdraw_throws400_whenNoPayoutMethodConfigured() {
        CreatorProfile cpNoPayment = new CreatorProfile();
        when(creatorProfileRepo.findByUserId(userId)).thenReturn(Optional.of(cpNoPayment));

        WithdrawRequest req = new WithdrawRequest();
        req.setAmount(new BigDecimal("50.00"));

        assertThatThrownBy(() -> walletService.withdraw(userId, req))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Payout method not configured");

        verify(walletTxRepo, never()).save(any());
    }

    @Test
    void withdraw_throws404_whenCreatorProfileNotFound() {
        when(creatorProfileRepo.findByUserId(userId)).thenReturn(Optional.empty());

        WithdrawRequest req = new WithdrawRequest();
        req.setAmount(new BigDecimal("50.00"));

        assertThatThrownBy(() -> walletService.withdraw(userId, req))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));

        verify(walletTxRepo, never()).save(any());
    }

    private WalletCallbackRequest signedCallback(Long transactionId, BigDecimal amount, String providerRef) {
        WalletCallbackRequest req = new WalletCallbackRequest();
        req.setTransactionId(transactionId);
        req.setAmount(amount);
        req.setProviderRef(providerRef);
        String payload = transactionId + ":" + amount.toPlainString() + ":" + providerRef;
        req.setSignature(HmacUtil.sha256Hex(payload, "test-callback-secret"));
        return req;
    }
}
