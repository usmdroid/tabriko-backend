package uz.tabriko.infrastructure.firebase;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class SecureOtpServiceTest {

    private static final String PHONE = "+998901234567";

    private final SecureOtpService otpService = new SecureOtpService();

    @Test
    void verifyOtp_rejectsHardcodedBackdoorCode_whenNoOtpWasSent() {
        assertThat(otpService.verifyOtp(PHONE, "2580")).isFalse();
    }

    @Test
    void verifyOtp_rejectsHardcodedBackdoorCode_evenWithPendingRealOtp() throws Exception {
        otpService.sendOtp(PHONE);
        String realOtp = extractStoredOtp(PHONE);

        if (!"2580".equals(realOtp)) {
            assertThat(otpService.verifyOtp(PHONE, "2580")).isFalse();
        }
    }

    @Test
    void verifyOtp_acceptsOnlyTheGeneratedCode() throws Exception {
        otpService.sendOtp(PHONE);
        String realOtp = extractStoredOtp(PHONE);
        String wrongCode = "0000".equals(realOtp) ? "1111" : "0000";

        assertThat(otpService.verifyOtp(PHONE, wrongCode)).isFalse();
        assertThat(otpService.verifyOtp(PHONE, realOtp)).isTrue();
    }

    @SuppressWarnings("unchecked")
    private String extractStoredOtp(String phone) throws Exception {
        Field storeField = SecureOtpService.class.getDeclaredField("store");
        storeField.setAccessible(true);
        ConcurrentHashMap<String, Object> store = (ConcurrentHashMap<String, Object>) storeField.get(otpService);
        Object entry = store.get(phone);
        Field otpField = entry.getClass().getDeclaredField("otp");
        otpField.setAccessible(true);
        return (String) otpField.get(entry);
    }
}
