package uz.tabriko.telegram.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uz.tabriko.domain.entity.CrashReport;
import uz.tabriko.telegram.entity.TelegramAlertSubscriber;
import uz.tabriko.telegram.repository.TelegramAlertSubscriberRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Forwards critical crash reports to a dedicated Telegram ALERT bot
 * (TELEGRAM_ALERT_BOT_TOKEN — separate from the Verify bot's TELEGRAM_BOT_TOKEN).
 * Recipients are every chat that has started the alert bot: chat ids are
 * collected by polling the bot's getUpdates, and each alert is broadcast to all
 * of them. A single-chat fallback (TELEGRAM_ALERT_CHAT_ID) is used only while
 * no subscribers have been collected yet.
 */
@Slf4j
@Service
public class DiagnosticsTelegramService {

    private static final long DEDUPE_WINDOW_MS = 300_000L;
    private static final int RATE_CAP_PER_MIN = 20;
    private static final long RATE_WINDOW_MS = 60_000L;
    private static final int MAX_MESSAGE_LEN = 4096;

    private final RestTemplate restTemplate;
    private final TelegramAlertSubscriberRepository subscriberRepo;

    @Value("${app.telegram.alert.bot-token:}")
    private String alertBotToken;

    @Value("${app.telegram.alert.chat-id:}")
    private String fallbackChatId;

    @Value("${app.telegram.alerts.enabled:true}")
    private boolean alertsEnabled;

    private final ConcurrentHashMap<String, Long> dedupeCache = new ConcurrentHashMap<>();
    private final AtomicInteger rateCounter = new AtomicInteger(0);
    private final AtomicLong rateWindowStart = new AtomicLong(System.currentTimeMillis());

    // getUpdates cursor (in-memory; on restart it resets to 0 and Telegram
    // re-delivers recent unconfirmed updates — upserts are idempotent by chat id).
    private final AtomicLong updateOffset = new AtomicLong(0);

    // Fire-and-forget send executor; overridden with a synchronous one in tests.
    private Executor sendExecutor = ForkJoinPool.commonPool();

    public DiagnosticsTelegramService(RestTemplate restTemplate,
                                      TelegramAlertSubscriberRepository subscriberRepo) {
        this.restTemplate = restTemplate;
        this.subscriberRepo = subscriberRepo;
    }

    // ── Alert broadcast ────────────────────────────────────────────────────────

    public void sendAlert(CrashReport report, UUID userId) {
        if (!alertsEnabled || alertBotToken == null || alertBotToken.isBlank()) {
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

        List<Long> recipients = resolveRecipients();
        if (recipients.isEmpty()) {
            log.debug("No Telegram alert subscribers yet — alert not delivered");
            return;
        }

        String text = buildMessage(report, userId);
        for (Long chatId : recipients) {
            sendExecutor.execute(() -> doSend(chatId, text));
        }
    }

    private List<Long> resolveRecipients() {
        List<Long> ids = new ArrayList<>();
        subscriberRepo.findAll().forEach(s -> ids.add(s.getChatId()));
        if (ids.isEmpty() && fallbackChatId != null && !fallbackChatId.isBlank()) {
            try {
                ids.add(Long.parseLong(fallbackChatId.trim()));
            } catch (NumberFormatException ignored) {
                // fallback chat id misconfigured — skip
            }
        }
        return ids;
    }

    private void doSend(long chatId, String text) {
        try {
            String url = "https://api.telegram.org/bot" + alertBotToken + "/sendMessage";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of("chat_id", chatId, "text", text);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Telegram alert to chat {} returned {}", chatId, response.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to send Telegram alert (TELEGRAM_ALERT_BOT_TOKEN) to chat {}: {}",
                chatId, e.getMessage());
        }
    }

    // ── Subscriber collection (getUpdates poll) ─────────────────────────────────

    @Scheduled(fixedDelay = 60_000L)
    public void pollSubscribers() {
        if (alertBotToken == null || alertBotToken.isBlank()) {
            return;
        }
        try {
            String url = "https://api.telegram.org/bot" + alertBotToken
                + "/getUpdates?timeout=0&allowed_updates=%5B%22message%22%5D&offset="
                + updateOffset.get();
            JsonNode resp = restTemplate.getForObject(url, JsonNode.class);
            if (resp == null || !resp.path("ok").asBoolean(false)) {
                return;
            }
            for (JsonNode upd : resp.path("result")) {
                updateOffset.set(upd.path("update_id").asLong() + 1);
                JsonNode chat = upd.path("message").path("chat");
                if (!chat.hasNonNull("id")) {
                    continue;
                }
                long chatId = chat.get("id").asLong();
                if (!subscriberRepo.existsById(chatId)) {
                    TelegramAlertSubscriber sub = new TelegramAlertSubscriber();
                    sub.setChatId(chatId);
                    sub.setUsername(chat.hasNonNull("username") ? chat.get("username").asText() : null);
                    sub.setFirstName(chat.hasNonNull("first_name") ? chat.get("first_name").asText() : null);
                    subscriberRepo.save(sub);
                    log.info("New Telegram alert subscriber: chat {}", chatId);
                }
            }
        } catch (Exception e) {
            // Non-fatal: if a webhook is set on the alert bot, getUpdates 409s — log only.
            log.warn("Telegram alert getUpdates poll failed: {}", e.getMessage());
        }
    }

    // ── Message formatting ──────────────────────────────────────────────────────

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
