package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.common.util.HmacUtil;
import uz.tabriko.domain.entity.WalletTransaction;
import uz.tabriko.domain.enums.TransactionStatus;
import uz.tabriko.domain.enums.TransactionType;
import uz.tabriko.dto.request.WalletCallbackRequest;
import uz.tabriko.infrastructure.payment.PaymentProvider;
import uz.tabriko.repository.OrderRepository;
import uz.tabriko.repository.UserRepository;
import uz.tabriko.repository.WalletTransactionRepository;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceCallbackTest {

    @Mock WalletTransactionRepository walletTxRepo;
    @Mock UserRepository userRepo;
    @Mock OrderRepository orderRepo;
    @Mock PaymentProvider paymentProvider;
    @Mock UserMapper mapper;

    @InjectMocks WalletService walletService;

    private static final String SECRET = "test-callback-secret";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(walletService, "commissionPercent", 15);
        ReflectionTestUtils.setField(walletService, "callbackSecret", SECRET);
    }

    // (a) HMAC invalid → rejected, transaction untouched
    @Test
    void handleCallback_invalidHmac_rejected() {
        WalletCallbackRequest req = new WalletCallbackRequest();
        req.setTransactionId(1L);
        req.setAmount(new BigDecimal("100.00"));
        req.setProviderRef("ref-bad");
        req.setSignature("wrong-signature");

        assertThatThrownBy(() -> walletService.handleCallback(req))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(401));

        verify(walletTxRepo, never()).findById(any());
        verify(walletTxRepo, never()).save(any());
    }

    // (b) Valid callback → balance credited (status set to COMPLETED, providerRef saved)
    @Test
    void handleCallback_validSignature_creditsBalance() {
        WalletTransaction tx = new WalletTransaction();
        tx.setId(2L);
        tx.setType(TransactionType.TOPUP);
        tx.setAmount(new BigDecimal("75.00"));
        tx.setStatus(TransactionStatus.PENDING);

        when(walletTxRepo.existsByProviderRef("ref-ok")).thenReturn(false);
        when(walletTxRepo.findById(2L)).thenReturn(Optional.of(tx));

        WalletCallbackRequest req = signedCallback(2L, new BigDecimal("75.00"), "ref-ok");
        walletService.handleCallback(req);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(tx.getProviderRef()).isEqualTo("ref-ok");
        verify(walletTxRepo).save(tx);
    }

    // (c) Duplicate providerRef → no double-credit
    @Test
    void handleCallback_duplicateProviderRef_idempotentNoDoubleCrdit() {
        when(walletTxRepo.existsByProviderRef("ref-dup")).thenReturn(true);

        WalletCallbackRequest req = signedCallback(3L, new BigDecimal("50.00"), "ref-dup");
        walletService.handleCallback(req);

        verify(walletTxRepo, never()).findById(any());
        verify(walletTxRepo, never()).save(any());
    }

    // Null providerRef skips the existsByProviderRef check and proceeds normally
    @Test
    void handleCallback_nullProviderRef_skipsIdempotencyCheck() {
        WalletTransaction tx = new WalletTransaction();
        tx.setId(4L);
        tx.setType(TransactionType.TOPUP);
        tx.setAmount(new BigDecimal("20.00"));
        tx.setStatus(TransactionStatus.PENDING);

        when(walletTxRepo.findById(4L)).thenReturn(Optional.of(tx));

        WalletCallbackRequest req = signedCallback(4L, new BigDecimal("20.00"), null);
        walletService.handleCallback(req);

        verify(walletTxRepo, never()).existsByProviderRef(any());
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(walletTxRepo).save(tx);
    }

    // creditTopUp with duplicate providerRef → early-return, no findById/save
    @Test
    void creditTopUp_duplicateProviderRef_earlyExit() {
        when(walletTxRepo.existsByProviderRef("click-tx-1")).thenReturn(true);

        walletService.creditTopUp(99L, new BigDecimal("200.00"), "click-tx-1");

        verify(walletTxRepo, never()).findById(any());
        verify(walletTxRepo, never()).save(any());
    }

    // creditTopUp: first call credited; second call with same providerRef is no-op
    @Test
    void creditTopUp_secondCallSameRef_idempotentNoDoubleSave() {
        WalletTransaction tx = new WalletTransaction();
        tx.setId(10L);
        tx.setType(TransactionType.TOPUP);
        tx.setAmount(new BigDecimal("50.00"));
        tx.setStatus(TransactionStatus.PENDING);

        when(walletTxRepo.existsByProviderRef("ref-once")).thenReturn(false, true);
        when(walletTxRepo.findById(10L)).thenReturn(Optional.of(tx));

        walletService.creditTopUp(10L, new BigDecimal("50.00"), "ref-once");
        walletService.creditTopUp(10L, new BigDecimal("50.00"), "ref-once");

        verify(walletTxRepo, times(1)).save(any());
    }

    private WalletCallbackRequest signedCallback(Long txId, BigDecimal amount, String providerRef) {
        WalletCallbackRequest req = new WalletCallbackRequest();
        req.setTransactionId(txId);
        req.setAmount(amount);
        req.setProviderRef(providerRef);
        String payload = txId + ":" + amount.toPlainString() + ":" + Objects.toString(providerRef, "");
        req.setSignature(HmacUtil.sha256Hex(payload, SECRET));
        return req;
    }
}
