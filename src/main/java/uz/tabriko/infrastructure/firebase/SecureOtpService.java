package uz.tabriko.infrastructure.firebase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production OTP service. Always active; MockOtpService (@Primary) overrides it in dev/test.
 *
 * Security properties:
 * - Cryptographically random 6-digit OTP (SecureRandom)
 * - TTL: 5 minutes
 * - Max 5 verification attempts before the entry is invalidated
 * - No static bypasses; no phone-number-derived codes
 *
 * SMS delivery: wire a real SMS gateway (e.g. Eskiz.uz, Playmobile) before launch.
 * Until then, sendOtp logs a WARNING — OTPs are not delivered to the user's handset.
 */
@Service
@Slf4j
public class SecureOtpService implements OtpService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long TTL_SECONDS = 300;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, OtpEntry> store = new ConcurrentHashMap<>();

    @Override
    public void sendOtp(String phone) {
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        store.put(phone, new OtpEntry(otp));
        // TODO: replace with real SMS gateway call (app.otp.sms-provider = eskiz | playmobile)
        log.warn("[OTP] SMS provider not configured — OTP for {} was NOT delivered via SMS", phone);
    }

    @Override
    public boolean verifyOtp(String phone, String code) {
        OtpEntry entry = store.get(phone);
        if (entry == null) return false;

        if (entry.isExpired()) {
            store.remove(phone);
            return false;
        }

        int attempt = entry.attempts.incrementAndGet();
        if (attempt > MAX_ATTEMPTS) {
            store.remove(phone);
            return false;
        }

        if (entry.otp.equals(code)) {
            store.remove(phone);
            return true;
        }
        return false;
    }

    @Scheduled(fixedDelay = 60_000)
    void evictExpired() {
        store.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private static final class OtpEntry {
        final String otp;
        final Instant expiresAt;
        final AtomicInteger attempts = new AtomicInteger(0);

        OtpEntry(String otp) {
            this.otp = otp;
            this.expiresAt = Instant.now().plusSeconds(TTL_SECONDS);
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
