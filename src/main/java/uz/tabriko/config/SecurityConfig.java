package uz.tabriko.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import uz.tabriko.security.AppCheckFilter;
import uz.tabriko.security.DeviceIntegrityFilter;
import uz.tabriko.security.JwtAccessDeniedHandler;
import uz.tabriko.security.JwtAuthEntryPoint;
import uz.tabriko.security.JwtAuthFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final DeviceIntegrityFilter deviceIntegrityFilter;
    private final AppCheckFilter appCheckFilter;
    private final JwtAuthEntryPoint authEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(
                    "/api/v1/ping",
                    "/api/v1/auth/**",
                    "/api/v1/applications/**",
                    "/api/v1/categories/**",
                    "/api/v1/catalog/**",
                    "/api/v1/creators/**",
                    "/api/v1/wallet/callback",
                    "/api/v1/wallet/callback/click",
                    "/api/v1/wallet/callback/payme",
                    "/api/v1/diagnostics/**",
                    "/api/v1/telegram/webhook",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    // Order-delivery media is deliberately excluded: /api/v1/media/signed and
                    // /files/media/** require authentication + ownership, unlike the folders below.
                    "/files/portfolio/**",
                    "/files/kyc/**",
                    "/files/applications/**"
                ).permitAll()
                // Admin only
                .requestMatchers("/api/v1/admin/**", "/api/v1/moderation/**")
                    .hasAnyRole("SUPERADMIN", "MODERATOR")
                // Authenticated
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(deviceIntegrityFilter, JwtAuthFilter.class)
            .addFilterAfter(appCheckFilter, DeviceIntegrityFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // allowedOriginPatterns supports wildcards (e.g. https://*.vercel.app) with credentials
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
