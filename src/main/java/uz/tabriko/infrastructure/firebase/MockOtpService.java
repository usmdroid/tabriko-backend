package uz.tabriko.infrastructure.firebase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Dev/test stub — active when app.integrations.live=false (the default).
@Service
@ConditionalOnProperty(name = "app.integrations.live", havingValue = "false", matchIfMissing = true)
@Slf4j
public class MockOtpService implements OtpService {

    // FIXME: development backdoor — remove before production launch.
    private static final String DEV_BACKDOOR_CODE = "2580";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final Map<String, String> store = new ConcurrentHashMap<>();

    @Override
    public void sendOtp(String phone) {
        String otp = String.format("%04d", SECURE_RANDOM.nextInt(10_000));
        store.put(phone, otp);
        log.info("[DEV] OTP for {}: {}", phone, otp);
    }

    @Override
    public boolean verifyOtp(String phone, String code) {
        if (DEV_BACKDOOR_CODE.equals(code)) {
            store.remove(phone);
            return true;
        }
        String stored = store.get(phone);
        if (stored != null && stored.equals(code)) {
            store.remove(phone);
            return true;
        }
        return false;
    }
}
