package uz.tabriko.infrastructure.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EskizSmsServiceTest {

    private static final String AUTH_URL = "https://notify.eskiz.uz/api/auth/login";
    private static final String SEND_URL = "https://notify.eskiz.uz/api/message/sms/send";
    private static final String TOKEN_RESPONSE = "{\"data\":{\"token\":\"test-token-123\"}}";
    private static final String SEND_RESPONSE = "{\"status\":\"waiting\"}";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private EskizSmsService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new EskizSmsService(restTemplate);
        ReflectionTestUtils.setField(service, "email", "test@tabriko.uz");
        ReflectionTestUtils.setField(service, "password", "secret");
        ReflectionTestUtils.setField(service, "from", "4546");
    }

    @Test
    void send_authenticatesAndSendsSms() {
        server.expect(once(), requestTo(AUTH_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(SEND_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess(SEND_RESPONSE, MediaType.APPLICATION_JSON));

        service.send("+998901234567", "Your OTP is 1234");

        server.verify();
    }

    @Test
    void send_cachesToken_noReauthOnSecondCall() {
        // Auth called once, send called twice
        server.expect(once(), requestTo(AUTH_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(times(2), requestTo(SEND_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess(SEND_RESPONSE, MediaType.APPLICATION_JSON));

        service.send("+998901234567", "First");
        service.send("+998907654321", "Second");

        server.verify();
    }

    @Test
    void send_reauthenticatesOn401() {
        // First auth
        server.expect(once(), requestTo(AUTH_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
        // First send fails with 401
        server.expect(once(), requestTo(SEND_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withUnauthorizedRequest());
        // Re-auth
        server.expect(once(), requestTo(AUTH_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
        // Second send succeeds
        server.expect(once(), requestTo(SEND_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess(SEND_RESPONSE, MediaType.APPLICATION_JSON));

        service.send("+998901234567", "Hello");

        server.verify();
    }

    @Test
    void authenticate_extractsTokenFromResponse() {
        server.expect(once(), requestTo(AUTH_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        String token = service.authenticate();

        assertThat(token).isEqualTo("test-token-123");
        server.verify();
    }

    @Test
    void send_tokenIsClearedOnReauth() {
        server.expect(once(), requestTo(AUTH_URL))
              .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(SEND_URL))
              .andRespond(withUnauthorizedRequest());
        server.expect(once(), requestTo(AUTH_URL))
              .andRespond(withSuccess("{\"data\":{\"token\":\"new-token-456\"}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(SEND_URL))
              .andRespond(withSuccess(SEND_RESPONSE, MediaType.APPLICATION_JSON));

        service.send("+998901234567", "Test");

        // After re-auth, cached token should be the new one
        @SuppressWarnings("unchecked")
        AtomicReference<String> cached = (AtomicReference<String>) ReflectionTestUtils.getField(service, "cachedToken");
        assertThat(cached.get()).isEqualTo("new-token-456");
        server.verify();
    }
}
