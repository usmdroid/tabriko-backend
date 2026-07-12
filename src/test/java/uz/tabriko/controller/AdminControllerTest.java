package uz.tabriko.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import uz.tabriko.dto.response.AdminStatsResponse;
import uz.tabriko.dto.response.AdminUserDetailResponse;
import uz.tabriko.dto.response.AdminUserResponse;
import uz.tabriko.dto.response.NotifyResultResponse;
import uz.tabriko.dto.response.OrderResponse;
import uz.tabriko.dto.response.PageResponse;
import uz.tabriko.dto.response.PlatformSettings;
import uz.tabriko.service.AdminService;

import java.time.Instant;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(AdminControllerTest.TestSecurityConfig.class)
class AdminControllerTest {

    @Autowired MockMvc mvc;

    @MockBean AdminService adminService;
    @MockBean uz.tabriko.service.OccasionService occasionService;
    @MockBean uz.tabriko.service.PromotionService promotionService;
    @MockBean uz.tabriko.service.AdminBroadcastService adminBroadcastService;
    @MockBean uz.tabriko.security.JwtUtil jwtUtil;
    @MockBean uz.tabriko.repository.UserDeviceRepository userDeviceRepository;
    @MockBean uz.tabriko.repository.PlatformSettingsRepository platformSettingsRepository;
    @MockBean uz.tabriko.security.AppCheckTokenVerifier appCheckTokenVerifier;

    /**
     * Minimal security config for the test slice:
     * - URL-level rules mirror production (/api/v1/admin/** → SUPERADMIN or MODERATOR)
     * - No JWT filter — @WithMockUser sets the SecurityContext directly
     * - @EnableMethodSecurity so @PreAuthorize annotations on controller methods are enforced
     */
    @org.springframework.boot.test.context.TestConfiguration
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

    // ===== MODERATOR read access =====

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_canGetUsers() throws Exception {
        when(adminService.getUsers(any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_canGetStats() throws Exception {
        when(adminService.getStats()).thenReturn(new AdminStatsResponse());

        mvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_canGetSettings() throws Exception {
        when(adminService.getSettings()).thenReturn(new PlatformSettings());

        mvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_canGetOrders() throws Exception {
        PageResponse<OrderResponse> emptyPage = new PageResponse<>();
        emptyPage.setContent(List.of());
        when(adminService.getAllOrders(anyInt(), anyInt())).thenReturn(emptyPage);

        mvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isOk());
    }

    // ===== MODERATOR blocked from write operations =====

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_cannotBlockUser_returns403() throws Exception {
        mvc.perform(post("/api/v1/admin/users/{id}/block", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_cannotUnblockUser_returns403() throws Exception {
        mvc.perform(post("/api/v1/admin/users/{id}/unblock", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_cannotPatchSettings_returns403() throws Exception {
        mvc.perform(patch("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenanceMode\":true}"))
                .andExpect(status().isForbidden());
    }

    // ===== SUPERADMIN full access =====

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void superadmin_canGetUsers() throws Exception {
        when(adminService.getUsers(any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void superadmin_canBlockUser() throws Exception {
        doNothing().when(adminService).blockUser(any());

        mvc.perform(post("/api/v1/admin/users/{id}/block", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void superadmin_canUnblockUser() throws Exception {
        doNothing().when(adminService).unblockUser(any());

        mvc.perform(post("/api/v1/admin/users/{id}/unblock", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void superadmin_canPatchSettings() throws Exception {
        when(adminService.updateSettings(any())).thenReturn(new PlatformSettings());

        mvc.perform(patch("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenanceMode\":true}"))
                .andExpect(status().isOk());
    }

    // ===== GET /admin/users/{id} — user detail =====

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_canGetUserDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminUserDetailResponse detail = new AdminUserDetailResponse(
                userId, "Alice", "+998901111111", "CLIENT", "ACTIVE", Instant.now(), "TBR-TEST001", List.of());
        when(adminService.getUserDetail(userId)).thenReturn(detail);

        mvc.perform(get("/api/v1/admin/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.role").value("CLIENT"))
                .andExpect(jsonPath("$.data.devices").isArray());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void getUserDetail_unknownUser_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminService.getUserDetail(userId))
                .thenThrow(uz.tabriko.common.exception.ApiException.notFound("User not found"));

        mvc.perform(get("/api/v1/admin/users/{id}", userId))
                .andExpect(status().isNotFound());
    }

    // ===== POST /admin/users/{id}/notify =====

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_canNotifyUser_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminService.notifyUser(eq(userId), any()))
                .thenReturn(new NotifyResultResponse(1, 1, 0));

        mvc.perform(post("/api/v1/admin/users/{id}/notify", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hello\",\"body\":\"World\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targeted").value(1))
                .andExpect(jsonPath("$.data.delivered").value(1));
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void notifyUser_missingTitle_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(post("/api/v1/admin/users/{id}/notify", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"World\"}"))
                .andExpect(status().isBadRequest());
    }

    // ===== Device block/unblock RBAC (scenarios 16, 17, 18) =====

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_canBlockDevice_returns200() throws Exception {
        doNothing().when(adminService).blockDevice(any());
        mvc.perform(post("/api/v1/admin/devices/{deviceId}/block", "dev-x"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void client_cannotBlockDevice_returns403() throws Exception {
        mvc.perform(post("/api/v1/admin/devices/{deviceId}/block", "dev-x"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void superadmin_canUnblockDevice_returns200() throws Exception {
        doNothing().when(adminService).unblockDevice(any());
        mvc.perform(post("/api/v1/admin/devices/{deviceId}/unblock", "dev-y"))
                .andExpect(status().isOk());
    }

    // ===== Unauthenticated =====

    @Test
    void unauthenticated_cannotAccessAdminEndpoints_returns401() throws Exception {
        mvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    // ===== Response shape: AdminUserResponse must not leak password/otp fields =====

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void getUsers_responseDoesNotContainPasswordOrOtp() throws Exception {
        AdminUserResponse u = new AdminUserResponse();
        u.setId(UUID.randomUUID());
        u.setName("Alice");
        u.setPhone("+998901111111");
        u.setStatus("active");

        when(adminService.getUsers(any(), any())).thenReturn(List.of(u));

        mvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[0].otp").doesNotExist())
                .andExpect(jsonPath("$.data[0].otpCode").doesNotExist())
                .andExpect(jsonPath("$.data[0].status").value("active"));
    }
}
