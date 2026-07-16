package uz.tabriko.telegram.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uz.tabriko.domain.entity.CrashReport;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class DiagnosticsTelegramService {

    private static final long DEDUPE_WINDOW_MS = 300_000L;
    private static final int RATE_CAP_PER_MIN = 20;
    private static final long RATE_WINDOW_MS = 60_000L;
    private static final int MAX_MESSAGE_LEN = 4096;

    private final RestTemplate restTemplate;

    @Value("${app.telegram.bot-token:}")
    private String botToken;

    @Value("${app.telegram.alert.chat-id:}")
    private String alertChatId;

    @Value("${app.telegram.alerts.enabled:true}")
    private boolean alertsEnabled;

    private final ConcurrentHashMap<String, Long> dedupeCache = new ConcurrentHashMap<>();
    private final AtomicInteger rateCounter = new AtomicInteger(0);
    private final AtomicLong rateWindowStart = new AtomicLong(System.currentTimeMillis());

    public DiagnosticsTelegramService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendAlert(CrashReport report, UUID userId) {
        if (!alertsEnabled || botToken == null || botToken.isBlank() || alertChatId == null || alertChatId.isBlank()) {
            return;
        }

        String dedupeKey = buildDedupeKey(report);
        long now = System.currentTimeMillis();

        Long lastSent = dedupeCache.get(dedupeKey);
        if (lastSent != null && (now - lastSent) < DEDUPE_WINDOW_MS) {
            return;
        }

        long windowStart = rateWindowStart.get();
        if (now - windowStart >= RATE_WINDOW_MS) {
            if (rateWindowStart.compareAndSet(windowStart, now)) {
                rateCounter.set(0);
            }
        }
        if (rateCounter.incrementAndGet() > RATE_CAP_PER_MIN) {
            return;
        }

        dedupeCache.put(dedupeKey, now);

        String text = buildMessage(report, userId);
        CompletableFuture.runAsync(() -> doSend(text));
    }

    private void doSend(String text) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of(
                "chat_id", alertChatId,
                "text", text
            );
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Telegram alert returned non-200 status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to send Telegram alert using [TELEGRAM_BOT_TOKEN]: {}", e.getMessage());
        }
    }

    private String buildMessage(CrashReport report, UUID userId) {
        String userStr = userId != null ? userId.toString() : "anon";
        String stackTrace = report.getStackTrace() != null ? report.getStackTrace() : "";

        String header = "🔴 " + report.getLevel() + "\n"
            + "app " + nullSafe(report.getAppVersion()) + " · "
            + nullSafe(report.getPlatform()) + " " + nullSafe(report.getOsVersion()) + " · "
            + nullSafe(report.getDeviceModel()) + "\n"
            + "user: " + userStr + "\n"
            + "screen: " + nullSafe(report.getScreen()) + "\n"
            + report.getMessage() + "\n\n";

        int available = MAX_MESSAGE_LEN - header.length();
        if (available > 0 && !stackTrace.isBlank()) {
            String truncated = stackTrace.length() > available ? stackTrace.substring(0, available) : stackTrace;
            return header + truncated;
        }
        return header.length() > MAX_MESSAGE_LEN ? header.substring(0, MAX_MESSAGE_LEN) : header;
    }

    private String buildDedupeKey(CrashReport report) {
        String firstLine = "";
        if (report.getStackTrace() != null) {
            int idx = report.getStackTrace().indexOf('\n');
            firstLine = idx > 0 ? report.getStackTrace().substring(0, idx) : report.getStackTrace();
        }
        return report.getMessage() + "|" + firstLine;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
