package uz.tabriko.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import uz.tabriko.controller.DiagnosticsController;
import uz.tabriko.service.DiagnosticsService;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DiagnosticsController.class)
@Import(DiagnosticsControllerTest.TestSecurityConfig.class)
class DiagnosticsControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean DiagnosticsService diagnosticsService;
    @MockBean uz.tabriko.security.JwtUtil jwtUtil;
    @MockBean uz.tabriko.repository.UserDeviceRepository userDeviceRepository;
    @MockBean uz.tabriko.repository.PlatformSettingsRepository platformSettingsRepository;
    @MockBean uz.tabriko.security.AppCheckTokenVerifier appCheckTokenVerifier;

    @org.springframework.boot.test.context.TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testChain(HttpSecurity http) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/v1/diagnostics/**").permitAll()
                    .anyRequest().authenticated()
                );
            return http.build();
        }
    }

    @Test
    void report_withValidCriticalBody_returns200() throws Exception {
        doNothing().when(diagnosticsService).report(any(), any());

        Map<String, Object> body = Map.of(
            "level", "CRITICAL",
            "message", "App crashed at startup"
        );

        mvc.perform(post("/api/v1/diagnostics/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void report_withoutAuthorizationHeader_returns200() throws Exception {
        doNothing().when(diagnosticsService).report(any(), isNull());

        Map<String, Object> body = Map.of(
            "level", "ERROR",
            "message", "Unhandled exception in payment flow"
        );

        mvc.perform(post("/api/v1/diagnostics/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void report_withMissingRequiredFields_returns200() throws Exception {
        Map<String, Object> body = Map.of("platform", "android");

        mvc.perform(post("/api/v1/diagnostics/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}
