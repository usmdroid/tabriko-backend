package uz.tabriko.infrastructure.firebase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Dev/test stub — logs OTP to console. @Primary so it wins over SecureOtpService in these profiles.
@Service
@Primary
@Profile({"dev", "test"})
@Slf4j
public class MockOtpService implements OtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final Map<String, String> store = new ConcurrentHashMap<>();

    @Override
    public void sendOtp(String phone) {
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        store.put(phone, otp);
        log.info("[DEV] OTP for {}: {}", phone, otp);
    }

    @Override
    public boolean verifyOtp(String phone, String code) {
        String stored = store.get(phone);
        if (stored != null && stored.equals(code)) {
            store.remove(phone);
            return true;
        }
        return false;
    }
}
