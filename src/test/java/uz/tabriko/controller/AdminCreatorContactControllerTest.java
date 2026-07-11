package uz.tabriko.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.dto.response.CreatorContactResponse;
import uz.tabriko.dto.response.CreatorResponse;
import uz.tabriko.service.AdminService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(AdminCreatorContactControllerTest.TestSecurityConfig.class)
class AdminCreatorContactControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean AdminService adminService;
    @MockBean uz.tabriko.service.OccasionService occasionService;
    @MockBean uz.tabriko.service.PromotionService promotionService;
    @MockBean uz.tabriko.service.AdminBroadcastService adminBroadcastService;
    @MockBean uz.tabriko.security.JwtUtil jwtUtil;

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

    // ===== GET /creators/{id} =====

    @Test
    @WithMockUser(roles = "MODERATOR")
    void getCreator_moderatorCanAccess() throws Exception {
        UUID id = UUID.randomUUID();
        CreatorResponse resp = new CreatorResponse();
        resp.setId(id);
        resp.setContacts(List.of());
        when(adminService.getCreatorDetail(id)).thenReturn(resp);

        mvc.perform(get("/api/v1/admin/creators/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contacts").isArray());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void getCreator_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(adminService.getCreatorDetail(id)).thenThrow(ApiException.notFound("Creator not found"));

        mvc.perform(get("/api/v1/admin/creators/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCreator_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/admin/creators/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ===== POST /creators/{id}/contacts =====

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void addContact_superadmin_success() throws Exception {
        UUID creatorId = UUID.randomUUID();
        CreatorContactResponse resp = new CreatorContactResponse();
        resp.setId(UUID.randomUUID());
        resp.setPhone("+998901234567");
        resp.setLabel("Manager");
        resp.setCreatedAt(Instant.now());
        when(adminService.addCreatorContact(eq(creatorId), any())).thenReturn(resp);

        mvc.perform(post("/api/v1/admin/creators/{id}/contacts", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+998901234567\",\"label\":\"Manager\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.phone").value("+998901234567"));
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void addContact_moderatorCanAccess() throws Exception {
        UUID creatorId = UUID.randomUUID();
        CreatorContactResponse resp = new CreatorContactResponse();
        resp.setId(UUID.randomUUID());
        resp.setPhone("+998901234568");
        resp.setCreatedAt(Instant.now());
        when(adminService.addCreatorContact(eq(creatorId), any())).thenReturn(resp);

        mvc.perform(post("/api/v1/admin/creators/{id}/contacts", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+998901234568\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void addContact_invalidPhone_returns400() throws Exception {
        UUID creatorId = UUID.randomUUID();

        mvc.perform(post("/api/v1/admin/creators/{id}/contacts", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"not-a-phone\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void addContact_missingPhone_returns400() throws Exception {
        UUID creatorId = UUID.randomUUID();

        mvc.perform(post("/api/v1/admin/creators/{id}/contacts", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Manager\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void addContact_creatorNotFound_returns404() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(adminService.addCreatorContact(eq(creatorId), any()))
                .thenThrow(ApiException.notFound("Creator not found"));

        mvc.perform(post("/api/v1/admin/creators/{id}/contacts", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+998901234567\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void addContact_duplicatePhone_returns400() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(adminService.addCreatorContact(eq(creatorId), any()))
                .thenThrow(ApiException.badRequest("Contact with this phone already exists"));

        mvc.perform(post("/api/v1/admin/creators/{id}/contacts", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+998901234567\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addContact_unauthenticated_returns401() throws Exception {
        mvc.perform(post("/api/v1/admin/creators/{id}/contacts", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+998901234567\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ===== DELETE /creators/{id}/contacts/{contactId} =====

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void deleteContact_superadmin_success() throws Exception {
        UUID creatorId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        doNothing().when(adminService).deleteCreatorContact(creatorId, contactId);

        mvc.perform(delete("/api/v1/admin/creators/{id}/contacts/{contactId}", creatorId, contactId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void deleteContact_moderatorCanAccess() throws Exception {
        UUID creatorId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        doNothing().when(adminService).deleteCreatorContact(creatorId, contactId);

        mvc.perform(delete("/api/v1/admin/creators/{id}/contacts/{contactId}", creatorId, contactId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void deleteContact_contactBelongsToDifferentCreator_returns404() throws Exception {
        UUID creatorId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        doThrow(ApiException.notFound("Contact not found"))
                .when(adminService).deleteCreatorContact(creatorId, contactId);

        mvc.perform(delete("/api/v1/admin/creators/{id}/contacts/{contactId}", creatorId, contactId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void deleteContact_unknownContactId_returns404() throws Exception {
        UUID creatorId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        doThrow(ApiException.notFound("Contact not found"))
                .when(adminService).deleteCreatorContact(creatorId, contactId);

        mvc.perform(delete("/api/v1/admin/creators/{id}/contacts/{contactId}", creatorId, contactId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteContact_unauthenticated_returns401() throws Exception {
        mvc.perform(delete("/api/v1/admin/creators/{id}/contacts/{contactId}",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ===== CLIENT role — 403 for all three endpoints =====

    @Test
    @WithMockUser(roles = "CLIENT")
    void getCreator_clientRole_returns403() throws Exception {
        mvc.perform(get("/api/v1/admin/creators/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void addContact_clientRole_returns403() throws Exception {
        mvc.perform(post("/api/v1/admin/creators/{id}/contacts", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+998901234567\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void deleteContact_clientRole_returns403() throws Exception {
        mvc.perform(delete("/api/v1/admin/creators/{id}/contacts/{contactId}",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

}
