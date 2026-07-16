package uz.tabriko.diagnostics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import uz.tabriko.domain.entity.CrashReport;
import uz.tabriko.telegram.service.DiagnosticsTelegramService;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DiagnosticsTelegramServiceTest {

    private static final String BOT_TOKEN = "test-bot-token";
    private static final String CHAT_ID = "-100123456789";
    private static final String TELEGRAM_URL = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private DiagnosticsTelegramService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new DiagnosticsTelegramService(restTemplate);
        ReflectionTestUtils.setField(service, "botToken", BOT_TOKEN);
        ReflectionTestUtils.setField(service, "alertChatId", CHAT_ID);
        ReflectionTestUtils.setField(service, "alertsEnabled", true);
    }

    private CrashReport buildReport(String message, String stackTrace) {
        CrashReport r = new CrashReport();
        r.setLevel("CRITICAL");
        r.setMessage(message);
        r.setStackTrace(stackTrace);
        r.setAppVersion("1.0.0");
        r.setPlatform("android");
        r.setOsVersion("14");
        r.setDeviceModel("Pixel 7");
        return r;
    }

    @Test
    void sendAlert_withValidConfig_callsTelegramOnce() throws Exception {
        server.expect(once(), requestToUriTemplate(TELEGRAM_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        service.sendAlert(buildReport("App crashed", "line1\nline2"), UUID.randomUUID());

        // Fire-and-forget: wait briefly for async
        Thread.sleep(200);
        server.verify();
    }

    @Test
    void sendAlert_withMissingToken_doesNotCallTelegram() throws Exception {
        ReflectionTestUtils.setField(service, "botToken", "");

        // No server expectation — any call would fail the test
        service.sendAlert(buildReport("App crashed", null), UUID.randomUUID());

        Thread.sleep(100);
        server.verify(); // zero expectations met = passes
    }

    @Test
    void sendAlert_duplicateWithinWindow_calledOnlyOnce() throws Exception {
        server.expect(once(), requestToUriTemplate(TELEGRAM_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        CrashReport report = buildReport("Duplicate crash", "stack\nline2");

        service.sendAlert(report, null);
        service.sendAlert(report, null); // same key — should be deduped

        Thread.sleep(200);
        server.verify();
    }

    @Test
    void sendAlert_telegramReturnsNon200_noExceptionThrown() {
        server.expect(once(), requestToUriTemplate(TELEGRAM_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withBadRequest());

        assertThatCode(() -> {
            service.sendAlert(buildReport("Bad gateway crash", "stack"), UUID.randomUUID());
            Thread.sleep(200);
        }).doesNotThrowAnyException();
    }
}
