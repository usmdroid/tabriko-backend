package uz.tabriko.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import uz.tabriko.dto.request.BroadcastNotificationRequest;
import uz.tabriko.dto.request.BroadcastTarget;
import uz.tabriko.dto.response.BroadcastResponse;
import uz.tabriko.domain.enums.BroadcastTargetType;
import uz.tabriko.service.AdminBroadcastService;
import uz.tabriko.service.AdminService;
import uz.tabriko.service.OccasionService;
import uz.tabriko.service.PromotionService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(AdminControllerTest.TestSecurityConfig.class)
class AdminBroadcastControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AdminService adminService;
    @MockBean OccasionService occasionService;
    @MockBean PromotionService promotionService;
    @MockBean AdminBroadcastService adminBroadcastService;
    @MockBean uz.tabriko.security.JwtUtil jwtUtil;
    @MockBean uz.tabriko.repository.UserDeviceRepository userDeviceRepository;
    @MockBean uz.tabriko.repository.PlatformSettingsRepository platformSettingsRepository;
    @MockBean uz.tabriko.security.AppCheckTokenVerifier appCheckTokenVerifier;

    private BroadcastNotificationRequest validRequest() {
        BroadcastTarget target = new BroadcastTarget();
        target.setType(BroadcastTargetType.ALL);
        BroadcastNotificationRequest req = new BroadcastNotificationRequest();
        req.setTitle("Test Title");
        req.setBody("Test Body");
        req.setTarget(target);
        return req;
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void superadmin_canBroadcast_returns200() throws Exception {
        when(adminBroadcastService.broadcast(any())).thenReturn(new BroadcastResponse(5, 8));

        mvc.perform(post("/api/v1/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users").value(5))
                .andExpect(jsonPath("$.data.devices").value(8));
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_canBroadcast_returns200() throws Exception {
        when(adminBroadcastService.broadcast(any())).thenReturn(new BroadcastResponse(3, 4));

        mvc.perform(post("/api/v1/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CREATOR")
    void creator_cannotBroadcast_returns403() throws Exception {
        mvc.perform(post("/api/v1/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void client_cannotBroadcast_returns403() throws Exception {
        mvc.perform(post("/api/v1/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void missingTitle_returns400() throws Exception {
        BroadcastTarget target = new BroadcastTarget();
        target.setType(BroadcastTargetType.ALL);
        BroadcastNotificationRequest req = new BroadcastNotificationRequest();
        req.setBody("Body only");
        req.setTarget(target);

        mvc.perform(post("/api/v1/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void missingBody_returns400() throws Exception {
        BroadcastTarget target = new BroadcastTarget();
        target.setType(BroadcastTargetType.ALL);
        BroadcastNotificationRequest req = new BroadcastNotificationRequest();
        req.setTitle("Title only");
        req.setTarget(target);

        mvc.perform(post("/api/v1/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void missingTarget_returns400() throws Exception {
        BroadcastNotificationRequest req = new BroadcastNotificationRequest();
        req.setTitle("Title");
        req.setBody("Body");
        // target is null

        mvc.perform(post("/api/v1/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void missingTargetType_returns400() throws Exception {
        BroadcastTarget target = new BroadcastTarget();
        // type is null
        BroadcastNotificationRequest req = new BroadcastNotificationRequest();
        req.setTitle("Title");
        req.setBody("Body");
        req.setTarget(target);

        mvc.perform(post("/api/v1/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mvc.perform(post("/api/v1/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }
}
