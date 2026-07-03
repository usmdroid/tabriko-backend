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
        String stored = store.get(phone);
        if (DEV_OTP.equals(code) || DEV_OTP.equals(stored)) {
            store.remove(phone);
            return true;
        }
        return false;
    }
}
