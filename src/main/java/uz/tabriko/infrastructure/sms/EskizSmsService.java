package uz.tabriko.infrastructure.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@ConditionalOnProperty(name = "app.integrations.live", havingValue = "true")
@Slf4j
public class EskizSmsService implements SmsService {

    private static final String AUTH_URL = "https://notify.eskiz.uz/api/auth/login";
    private static final String SEND_URL = "https://notify.eskiz.uz/api/message/sms/send";

    private final RestTemplate restTemplate;
    private final AtomicReference<String> cachedToken = new AtomicReference<>();

    @Value("${ESKIZ_EMAIL}")
    private String email;

    @Value("${ESKIZ_PASSWORD}")
    private String password;

    @Value("${ESKIZ_FROM:4546}")
    private String from;

    public EskizSmsService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void send(String phone, String message) {
        String token = getOrRefreshToken();
        try {
            doSend(phone, message, token);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("[ESKIZ] Token expired, re-authenticating");
            cachedToken.set(null);
            token = getOrRefreshToken();
            doSend(phone, message, token);
        }
    }

    private String getOrRefreshToken() {
        String token = cachedToken.get();
        if (token != null) return token;
        token = authenticate();
        cachedToken.set(token);
        return token;
    }

    String authenticate() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("email", email, "password", password);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        @SuppressWarnings("unchecked")
        ResponseEntity<Map> response = restTemplate.exchange(AUTH_URL, HttpMethod.POST, request, Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        String token = (String) data.get("token");
        log.info("[ESKIZ] Authenticated successfully");
        return token;
    }

    private void doSend(String phone, String message, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBearerAuth(token);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("mobile_phone", phone);
        body.add("message", message);
        body.add("from", from);
        body.add("callback_url", "");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        restTemplate.postForObject(SEND_URL, request, Map.class);
        log.info("[ESKIZ] SMS sent to {}", phone);
    }
}
