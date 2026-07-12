package uz.tabriko.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.WalletTransaction;
import uz.tabriko.domain.enums.TransactionStatus;
import uz.tabriko.domain.enums.TransactionType;
import uz.tabriko.repository.WalletTransactionRepository;
import uz.tabriko.service.WalletService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymeCallbackControllerTest {

    private static final String PAYME_KEY = "test-payme-key";
    private static final String VALID_AUTH = "Basic " + Base64.getEncoder().encodeToString(("Paycom:" + PAYME_KEY).getBytes(StandardCharsets.UTF_8));

    @Mock WalletService walletService;
    @Mock WalletTransactionRepository walletTxRepo;

    @InjectMocks PaymeCallbackController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "paymeKey", PAYME_KEY);
    }

    // --- Auth ---

    @Test
    void handle_invalidAuth_returnsError32504() {
        Map<String, Object> body = rpcBody("PerformTransaction", Map.of("id", "PAYME-1"));

        ResponseEntity<Map<String, Object>> resp = controller.handle("Basic wrong", body);

        assertErrorCode(resp, -32504);
        verify(walletService, never()).creditTopUp(any(), any(), any());
    }

    @Test
    void handle_missingAuth_returnsError32504() {
        Map<String, Object> body = rpcBody("PerformTransaction", Map.of("id", "PAYME-1"));

        ResponseEntity<Map<String, Object>> resp = controller.handle(null, body);

        assertErrorCode(resp, -32504);
    }

    // --- PerformTransaction ---

    @Test
    void performTransaction_validAuth_creditsWallet() {
        WalletTransaction tx = pendingTx(42L, new BigDecimal("100.00"));
        when(walletTxRepo.findByProviderRef("PAYME-42-abc")).thenReturn(Optional.of(tx));

        Map<String, Object> params = Map.of("id", "PAYME-42-abc", "amount", 10000);
        Map<String, Object> body = rpcBody("PerformTransaction", params);

        ResponseEntity<Map<String, Object>> resp = controller.handle(VALID_AUTH, body);

        assertThat(resp.getBody()).containsKey("result");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resp.getBody().get("result");
        assertThat(result.get("state")).isEqualTo(2);
        verify(walletService).creditTopUp(eq(42L), eq(new BigDecimal("100.00")), eq("PAYME-42-abc"));
    }

    @Test
    void performTransaction_duplicateCall_idempotentViaService() {
        WalletTransaction tx = pendingTx(42L, new BigDecimal("50.00"));
        when(walletTxRepo.findByProviderRef("PAYME-42-abc")).thenReturn(Optional.of(tx));

        Map<String, Object> params = Map.of("id", "PAYME-42-abc", "amount", 5000);
        Map<String, Object> body = rpcBody("PerformTransaction", params);

        controller.handle(VALID_AUTH, body);
        controller.handle(VALID_AUTH, body);

        // service called twice; it handles idempotency by status check
        verify(walletService, times(2)).creditTopUp(eq(42L), any(), eq("PAYME-42-abc"));
    }

    @Test
    void performTransaction_unknownPaymeId_returnsError32501() {
        when(walletTxRepo.findByProviderRef("UNKNOWN")).thenReturn(Optional.empty());

        Map<String, Object> params = Map.of("id", "UNKNOWN", "amount", 10000);
        ResponseEntity<Map<String, Object>> resp = controller.handle(VALID_AUTH, rpcBody("PerformTransaction", params));

        assertErrorCode(resp, -32501);
        verify(walletService, never()).creditTopUp(any(), any(), any());
    }

    // --- CancelTransaction ---

    @Test
    void cancelTransaction_pending_marksCancelled() {
        WalletTransaction tx = pendingTx(10L, new BigDecimal("75.00"));
        when(walletTxRepo.findByProviderRef("PAYME-10-xyz")).thenReturn(Optional.of(tx));

        Map<String, Object> params = Map.of("id", "PAYME-10-xyz", "reason", 1);
        ResponseEntity<Map<String, Object>> resp = controller.handle(VALID_AUTH, rpcBody("CancelTransaction", params));

        assertThat(resp.getBody()).containsKey("result");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resp.getBody().get("result");
        assertThat(result.get("state")).isEqualTo(-1);
        verify(walletService).cancelTopUp(10L);
    }

    @Test
    void cancelTransaction_completed_returnsError32503() {
        WalletTransaction tx = pendingTx(10L, new BigDecimal("75.00"));
        when(walletTxRepo.findByProviderRef("PAYME-10-xyz")).thenReturn(Optional.of(tx));
        doThrow(ApiException.badRequest("Cannot cancel a completed transaction"))
            .when(walletService).cancelTopUp(10L);

        Map<String, Object> params = Map.of("id", "PAYME-10-xyz", "reason", 1);
        ResponseEntity<Map<String, Object>> resp = controller.handle(VALID_AUTH, rpcBody("CancelTransaction", params));

        assertErrorCode(resp, -32503);
    }

    // --- CheckPerformTransaction ---

    @Test
    void checkPerformTransaction_knownAccount_returnsAllow() {
        when(walletTxRepo.existsById(5L)).thenReturn(true);

        Map<String, Object> account = Map.of("account_id", "5");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("amount", 5000);
        params.put("account", account);
        ResponseEntity<Map<String, Object>> resp = controller.handle(VALID_AUTH, rpcBody("CheckPerformTransaction", params));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resp.getBody().get("result");
        assertThat(result.get("allow")).isEqualTo(true);
    }

    @Test
    void checkPerformTransaction_unknownAccount_returnsError31050() {
        when(walletTxRepo.existsById(99L)).thenReturn(false);

        Map<String, Object> account = Map.of("account_id", "99");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("amount", 5000);
        params.put("account", account);
        ResponseEntity<Map<String, Object>> resp = controller.handle(VALID_AUTH, rpcBody("CheckPerformTransaction", params));

        assertErrorCode(resp, -31050);
    }

    // --- Helpers ---

    private WalletTransaction pendingTx(Long id, BigDecimal amount) {
        WalletTransaction tx = new WalletTransaction();
        tx.setId(id);
        tx.setType(TransactionType.TOPUP);
        tx.setAmount(amount);
        tx.setStatus(TransactionStatus.PENDING);
        return tx;
    }

    private Map<String, Object> rpcBody(String method, Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", 1);
        body.put("method", method);
        body.put("params", params);
        return body;
    }

    @SuppressWarnings("unchecked")
    private void assertErrorCode(ResponseEntity<Map<String, Object>> resp, int expectedCode) {
        assertThat(resp.getBody()).containsKey("error");
        Map<String, Object> error = (Map<String, Object>) resp.getBody().get("error");
        assertThat(error.get("code")).isEqualTo(expectedCode);
    }
}
