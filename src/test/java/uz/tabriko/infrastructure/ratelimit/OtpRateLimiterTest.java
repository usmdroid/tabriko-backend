package uz.tabriko.infrastructure.ratelimit;

import org.junit.jupiter.api.Test;
import uz.tabriko.common.exception.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpRateLimiterTest {

    private static final String PHONE = "+998901234567";
    private static final String IP = "1.2.3.4";

    @Test
    void checkAndRecord_underPhoneLimit_allowed() {
        OtpRateLimiter limiter = new OtpRateLimiter(3, 60, 10, 900);

        limiter.checkAndRecord(PHONE, IP);
        limiter.checkAndRecord(PHONE, IP);
        limiter.checkAndRecord(PHONE, IP);
        // No exception thrown for the first 3 requests (limit = 3)
    }

    @Test
    void checkAndRecord_exceedsPerPhoneLimit_throws429() {
        OtpRateLimiter limiter = new OtpRateLimiter(3, 60, 10, 900);

        limiter.checkAndRecord(PHONE, IP);
        limiter.checkAndRecord(PHONE, IP);
        limiter.checkAndRecord(PHONE, IP);

        assertThatThrownBy(() -> limiter.checkAndRecord(PHONE, IP))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(429));
    }

    @Test
    void checkAndRecord_exceedsPerIpLimit_acrossDifferentPhones_throws429() {
        OtpRateLimiter limiter = new OtpRateLimiter(100, 60, 2, 900);

        limiter.checkAndRecord("+998900000001", IP);
        limiter.checkAndRecord("+998900000002", IP);

        assertThatThrownBy(() -> limiter.checkAndRecord("+998900000003", IP))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(429));
    }

    @Test
    void checkAndRecord_differentPhones_doNotShareLimit() {
        OtpRateLimiter limiter = new OtpRateLimiter(1, 60, 10, 900);

        limiter.checkAndRecord("+998900000001", IP);
        limiter.checkAndRecord("+998900000002", IP);
        // Each phone has its own bucket; neither exceeds its own limit of 1
    }

    @Test
    void checkAndRecord_windowExpired_resetsLimit() throws InterruptedException {
        OtpRateLimiter limiter = new OtpRateLimiter(1, 0, 10, 900);

        limiter.checkAndRecord(PHONE, IP);
        // Window of 0 seconds means the entry is immediately stale on the next check
        Thread.sleep(5);
        limiter.checkAndRecord(PHONE, IP);
    }
}
