package uz.tabriko.infrastructure.firebase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.domain.entity.OtpCode;
import uz.tabriko.infrastructure.sms.SmsService;
import uz.tabriko.repository.OtpCodeRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

/**
 * Production OTP service. Active when app.integrations.live=true.
 * Sends OTP via EskizSmsService and persists codes in DB.
 */
@Service
@ConditionalOnProperty(name = "app.integrations.live", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SecureOtpService implements OtpService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long TTL_SECONDS = 300;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OtpCodeRepository otpCodeRepo;
    private final SmsService smsService;

    @Override
    @Transactional
    public void sendOtp(String phone) {
        String otp = String.format("%04d", SECURE_RANDOM.nextInt(10_000));
        OtpCode entry = otpCodeRepo.findByPhone(phone).orElseGet(OtpCode::new);
        entry.setPhone(phone);
        entry.setCode(otp);
        entry.setExpiresAt(Instant.now().plusSeconds(TTL_SECONDS));
        entry.setAttempts(0);
        otpCodeRepo.save(entry);
        smsService.send(phone, "Tabriko: your verification code is " + otp);
    }

    @Override
    @Transactional
    public boolean verifyOtp(String phone, String code) {
        Optional<OtpCode> maybe = otpCodeRepo.findByPhone(phone);
        if (maybe.isEmpty()) return false;
        OtpCode entry = maybe.get();

        if (entry.isExpired()) {
            otpCodeRepo.delete(entry);
            return false;
        }

        int attempt = entry.getAttempts() + 1;
        if (attempt > MAX_ATTEMPTS) {
            otpCodeRepo.delete(entry);
            return false;
        }

        if (entry.getCode().equals(code)) {
            otpCodeRepo.delete(entry);
            return true;
        }

        entry.setAttempts(attempt);
        otpCodeRepo.save(entry);
        return false;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    void evictExpired() {
        otpCodeRepo.deleteExpired(Instant.now());
    }
}
