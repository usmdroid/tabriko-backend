package uz.tabriko.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import uz.tabriko.repository.WalletTransactionRepository;
import uz.tabriko.service.WalletService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClickCallbackControllerTest {

    private static final String SERVICE_ID = "1234";
    private static final String SECRET_KEY = "test-secret";
    private static final String CLICK_TRANS_ID = "TX-001";
    private static final String MERCHANT_TRANS_ID = "42";
    private static final String AMOUNT_STR = "100.00";
    private static final String SIGN_TIME = "2024-01-01 12:00:00";

    @Mock WalletService walletService;
    @Mock WalletTransactionRepository walletTxRepo;

    @InjectMocks ClickCallbackController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "serviceId", SERVICE_ID);
        ReflectionTestUtils.setField(controller, "secretKey", SECRET_KEY);
    }

    @Test
    void handle_validSignatureActionComplete_creditsWallet() {
        String sign = md5(CLICK_TRANS_ID + SERVICE_ID + SECRET_KEY + MERCHANT_TRANS_ID + AMOUNT_STR + 1 + SIGN_TIME);

        ResponseEntity<Map<String, Object>> resp = controller.handle(
            CLICK_TRANS_ID, SERVICE_ID, "DOC-1", MERCHANT_TRANS_ID,
            AMOUNT_STR, 1, 0, "Success", SIGN_TIME, sign
        );

        assertThat(resp.getBody()).containsEntry("error", 0);
        verify(walletService).creditTopUp(eq(42L), eq(new BigDecimal(AMOUNT_STR)), eq(CLICK_TRANS_ID));
    }

    @Test
    void handle_invalidSignature_returnsError() {
        ResponseEntity<Map<String, Object>> resp = controller.handle(
            CLICK_TRANS_ID, SERVICE_ID, "DOC-1", MERCHANT_TRANS_ID,
            AMOUNT_STR, 1, 0, "Success", SIGN_TIME, "wrong-signature"
        );

        assertThat(resp.getBody()).containsEntry("error", -1);
        verify(walletService, never()).creditTopUp(any(), any(), any());
    }

    @Test
    void handle_duplicateCallback_idempotentNoDoubleCreditViaService() {
        // WalletService.creditTopUp is idempotent by design; controller just calls it
        String sign = md5(CLICK_TRANS_ID + SERVICE_ID + SECRET_KEY + MERCHANT_TRANS_ID + AMOUNT_STR + 1 + SIGN_TIME);

        controller.handle(CLICK_TRANS_ID, SERVICE_ID, "DOC-1", MERCHANT_TRANS_ID, AMOUNT_STR, 1, 0, "Success", SIGN_TIME, sign);
        controller.handle(CLICK_TRANS_ID, SERVICE_ID, "DOC-1", MERCHANT_TRANS_ID, AMOUNT_STR, 1, 0, "Success", SIGN_TIME, sign);

        // service called twice; service itself is idempotent
        verify(walletService, times(2)).creditTopUp(eq(42L), any(), eq(CLICK_TRANS_ID));
    }

    @Test
    void handle_paymentError_acknowledgesWithoutCrediting() {
        String sign = md5(CLICK_TRANS_ID + SERVICE_ID + SECRET_KEY + MERCHANT_TRANS_ID + AMOUNT_STR + 1 + SIGN_TIME);

        ResponseEntity<Map<String, Object>> resp = controller.handle(
            CLICK_TRANS_ID, SERVICE_ID, "DOC-1", MERCHANT_TRANS_ID,
            AMOUNT_STR, 1, -9, "Cancelled by user", SIGN_TIME, sign
        );

        assertThat(resp.getBody()).containsEntry("error", 0);
        verify(walletService, never()).creditTopUp(any(), any(), any());
    }

    @Test
    void handle_actionPrepare_knownTx_returnsSuccess() {
        when(walletTxRepo.existsById(42L)).thenReturn(true);
        String sign = md5(CLICK_TRANS_ID + SERVICE_ID + SECRET_KEY + MERCHANT_TRANS_ID + AMOUNT_STR + 0 + SIGN_TIME);

        ResponseEntity<Map<String, Object>> resp = controller.handle(
            CLICK_TRANS_ID, SERVICE_ID, "DOC-1", MERCHANT_TRANS_ID,
            AMOUNT_STR, 0, 0, "Success", SIGN_TIME, sign
        );

        assertThat(resp.getBody()).containsEntry("error", 0);
        verify(walletService, never()).creditTopUp(any(), any(), any());
    }

    @Test
    void handle_actionPrepare_unknownTx_returnsNotFound() {
        when(walletTxRepo.existsById(42L)).thenReturn(false);
        String sign = md5(CLICK_TRANS_ID + SERVICE_ID + SECRET_KEY + MERCHANT_TRANS_ID + AMOUNT_STR + 0 + SIGN_TIME);

        ResponseEntity<Map<String, Object>> resp = controller.handle(
            CLICK_TRANS_ID, SERVICE_ID, "DOC-1", MERCHANT_TRANS_ID,
            AMOUNT_STR, 0, 0, "Success", SIGN_TIME, sign
        );

        assertThat(resp.getBody()).containsEntry("error", -5);
        verify(walletService, never()).creditTopUp(any(), any(), any());
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
