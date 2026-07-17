package uz.tabriko.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.dto.response.CreatorResponse;
import uz.tabriko.service.AdminService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(AdminCreatorAvatarControllerTest.TestSecurityConfig.class)
class AdminCreatorAvatarControllerTest {

    @Autowired MockMvc mvc;

    @MockBean AdminService adminService;
    @MockBean uz.tabriko.service.OccasionService occasionService;
    @MockBean uz.tabriko.service.PromotionService promotionService;
    @MockBean uz.tabriko.service.AdminBroadcastService adminBroadcastService;
    @MockBean uz.tabriko.service.RequisiteService requisiteService;
    @MockBean uz.tabriko.service.ModerationService moderationService;
    @MockBean uz.tabriko.security.JwtUtil jwtUtil;
    @MockBean uz.tabriko.repository.UserDeviceRepository userDeviceRepository;
    @MockBean uz.tabriko.repository.PlatformSettingsRepository platformSettingsRepository;
    @MockBean uz.tabriko.security.AppCheckTokenVerifier appCheckTokenVerifier;

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testChain(HttpSecurity http) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((req, res, e) -> res.sendError(401, "Unauthorized"))
                    .accessDeniedHandler((req, res, e) -> res.sendError(403, "Forbidden"))
                )
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/v1/admin/**").hasAnyRole("SUPERADMIN", "MODERATOR")
                    .anyRequest().authenticated()
                );
            return http.build();
        }
    }

    private static MockMultipartFile validImage() {
        return new MockMultipartFile("file", "avatar.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00, 0x01});
    }

    // ===== SUPERADMIN success =====

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void superadmin_uploadAvatar_returns200WithAvatarUrl() throws Exception {
        UUID id = UUID.randomUUID();
        CreatorResponse resp = new CreatorResponse();
        resp.setId(id);
        resp.setAvatarUrl("http://localhost:8080/files/avatars/test.jpg");
        when(adminService.uploadCreatorAvatar(eq(id), any())).thenReturn(resp);

        mvc.perform(multipart("/api/v1/admin/creators/{id}/avatar", id)
                        .file(validImage()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("http://localhost:8080/files/avatars/test.jpg"));
    }

    // ===== MODERATOR is blocked (SUPERADMIN-only) =====

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_uploadAvatar_returns403() throws Exception {
        mvc.perform(multipart("/api/v1/admin/creators/{id}/avatar", UUID.randomUUID())
                        .file(validImage()))
                .andExpect(status().isForbidden());
    }

    // ===== Unauthenticated =====

    @Test
    void unauthenticated_uploadAvatar_returns401() throws Exception {
        mvc.perform(multipart("/api/v1/admin/creators/{id}/avatar", UUID.randomUUID())
                        .file(validImage()))
                .andExpect(status().isUnauthorized());
    }

    // ===== Creator not found =====

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void uploadAvatar_creatorNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(adminService.uploadCreatorAvatar(eq(id), any()))
                .thenThrow(ApiException.notFound("Creator not found"));

        mvc.perform(multipart("/api/v1/admin/creators/{id}/avatar", id)
                        .file(validImage()))
                .andExpect(status().isNotFound());
    }

    // ===== Validation failures delegated to service =====

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void uploadAvatar_nonImageFile_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(adminService.uploadCreatorAvatar(eq(id), any()))
                .thenThrow(ApiException.badRequest("Only image files are allowed"));

        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf",
                MediaType.APPLICATION_PDF_VALUE, new byte[]{0x25, 0x50, 0x44, 0x46});

        mvc.perform(multipart("/api/v1/admin/creators/{id}/avatar", id)
                        .file(pdf))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void uploadAvatar_oversizedFile_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(adminService.uploadCreatorAvatar(eq(id), any()))
                .thenThrow(ApiException.badRequest("File size must not exceed 5 MB"));

        mvc.perform(multipart("/api/v1/admin/creators/{id}/avatar", id)
                        .file(validImage()))
                .andExpect(status().isBadRequest());
    }
}
