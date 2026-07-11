package uz.tabriko.telegram.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.tabriko.security.JwtUtil;
import uz.tabriko.telegram.service.TelegramBotService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelegramWebhookController.class)
@TestPropertySource(properties = "app.telegram.webhook-secret=test-secret")
@Import(TelegramWebhookControllerTest.TestSecurityConfig.class)
class TelegramWebhookControllerTest {

    @Autowired MockMvc mvc;
    @Autowired TelegramWebhookController controller;
    @MockBean TelegramBotService botService;
    @MockBean JwtUtil jwtUtil;
    @MockBean uz.tabriko.repository.UserDeviceRepository userDeviceRepository;
    @MockBean uz.tabriko.repository.PlatformSettingsRepository platformSettingsRepository;
    @MockBean uz.tabriko.security.AppCheckTokenVerifier appCheckTokenVerifier;

    private static final String SECRET = "test-secret";
    private static final String UPDATE_JSON = "{\"update_id\":1}";

    @BeforeEach
    void resetSecret() {
        ReflectionTestUtils.setField(controller, "webhookSecret", SECRET);
    }

    @org.springframework.boot.test.context.TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
        }
    }

    // ===== Webhook auth =====

    @Test
    void webhook_secretBlank_returns401() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");
        mvc.perform(post("/api/v1/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_JSON))
            .andExpect(status().isUnauthorized());
        verify(botService, never()).handleUpdate(any());
    }

    @Test
    void webhook_secretNull_returns401() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookSecret", null);
        mvc.perform(post("/api/v1/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_JSON))
            .andExpect(status().isUnauthorized());
        verify(botService, never()).handleUpdate(any());
    }

    @Test
    void webhook_headerMissing_returns401() throws Exception {
        mvc.perform(post("/api/v1/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_JSON))
            .andExpect(status().isUnauthorized());
        verify(botService, never()).handleUpdate(any());
    }

    @Test
    void webhook_headerWrong_returns401() throws Exception {
        mvc.perform(post("/api/v1/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_JSON))
            .andExpect(status().isUnauthorized());
        verify(botService, never()).handleUpdate(any());
    }

    @Test
    void webhook_headerCorrect_returns200AndCallsService() throws Exception {
        mvc.perform(post("/api/v1/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_JSON))
            .andExpect(status().isOk());
        verify(botService).handleUpdate(any(Update.class));
    }
}
