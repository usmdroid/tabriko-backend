package uz.tabriko.infrastructure.firebase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Mock OTP service for dev — always accepts "123456"
@Service
@Slf4j
public class MockOtpService implements OtpService {

    private static final String DEV_OTP = "123456";
    private final Map<String, String> store = new ConcurrentHashMap<>();

    @Override
    public void sendOtp(String phone) {
        store.put(phone, DEV_OTP);
        log.info("[DEV] OTP for {} is {}", phone, DEV_OTP);
    }

    @Override
    public boolean verifyOtp(String phone, String code) {
        // FIXME: temporary dev bypass — accept the last 4 digits of the phone number
        // as a valid OTP for ANY phone. Remove before production.
        String lastFour = lastFourDigits(phone);
        if (lastFour != null && lastFour.equals(code)) {
            store.remove(phone);
            return true;
        }

        String stored = store.get(phone);
        if (DEV_OTP.equals(code) || DEV_OTP.equals(stored)) {
            store.remove(phone);
            return true;
        }
        return false;
    }

    // FIXME: helper for the dev bypass above — remove together with it.
    private String lastFourDigits(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 4) return null;
        return digits.substring(digits.length() - 4);
    }
}
