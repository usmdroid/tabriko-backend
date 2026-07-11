package uz.tabriko.infrastructure.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.tabriko.common.exception.ApiException;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

// Sliding-window rate limiter for POST /auth/login, keyed by phone and by client IP.
@Component
public class LoginRateLimiter {

    private final int perPhoneMax;
    private final long perPhoneWindowSeconds;
    private final int perIpMax;
    private final long perIpWindowSeconds;

    private final ConcurrentHashMap<String, Deque<Instant>> phoneHits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> ipHits = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${app.login.rate-limit.per-phone-max:10}") int perPhoneMax,
            @Value("${app.login.rate-limit.per-phone-window-seconds:60}") long perPhoneWindowSeconds,
            @Value("${app.login.rate-limit.per-ip-max:30}") int perIpMax,
            @Value("${app.login.rate-limit.per-ip-window-seconds:900}") long perIpWindowSeconds
    ) {
        this.perPhoneMax = perPhoneMax;
        this.perPhoneWindowSeconds = perPhoneWindowSeconds;
        this.perIpMax = perIpMax;
        this.perIpWindowSeconds = perIpWindowSeconds;
    }

    public void checkAndRecord(String phone, String ip) {
        if (!allow(phoneHits, phone, perPhoneMax, perPhoneWindowSeconds)) {
            throw ApiException.tooManyRequests("Too many login attempts for this phone number. Please try again later.");
        }
        if (ip != null && !allow(ipHits, ip, perIpMax, perIpWindowSeconds)) {
            throw ApiException.tooManyRequests("Too many login attempts from this network. Please try again later.");
        }
    }

    private boolean allow(ConcurrentHashMap<String, Deque<Instant>> hits, String key, int max, long windowSeconds) {
        Deque<Instant> deque = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        Instant cutoff = Instant.now().minusSeconds(windowSeconds);
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() >= max) {
                return false;
            }
            deque.addLast(Instant.now());
        }
        return true;
    }
}
