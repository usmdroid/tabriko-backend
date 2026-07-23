package uz.tabriko.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.ContactsService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContactsController.class)
@Import(ContactsControllerTest.TestSecurityConfig.class)
class ContactsControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ContactsService contactsService;
    @MockBean uz.tabriko.security.JwtUtil jwtUtil;
    @MockBean uz.tabriko.repository.UserDeviceRepository userDeviceRepository;
    @MockBean uz.tabriko.repository.PlatformSettingsRepository platformSettingsRepository;
    @MockBean uz.tabriko.security.AppCheckTokenVerifier appCheckTokenVerifier;

    private static final UsernamePasswordAuthenticationToken TEST_AUTH =
            new UsernamePasswordAuthenticationToken(
                    new UserPrincipal(UUID.randomUUID(), "+998901234567", "CLIENT"),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));

    @org.springframework.boot.test.context.TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testChain(HttpSecurity http) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            return http.build();
        }
    }

    @Test
    void twoThousandAndOneHashes_returns400() throws Exception {
        List<String> hashes = IntStream.range(0, 2001)
                .mapToObj(i -> String.format("%064x", i))
                .collect(Collectors.toList());

        mvc.perform(post("/api/v1/me/contacts/birthdays")
                .with(authentication(TEST_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("hashes", hashes))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exactlyTwoThousandHashes_returns200() throws Exception {
        List<String> hashes = IntStream.range(0, 2000)
                .mapToObj(i -> String.format("%064x", i))
                .collect(Collectors.toList());

        when(contactsService.findBirthdayMatches(any(), any())).thenReturn(Collections.emptyList());

        mvc.perform(post("/api/v1/me/contacts/birthdays")
                .with(authentication(TEST_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("hashes", hashes))))
                .andExpect(status().isOk());
    }
}
