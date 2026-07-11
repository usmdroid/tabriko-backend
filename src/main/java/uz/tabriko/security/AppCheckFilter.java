package uz.tabriko.security;

import com.google.firebase.FirebaseApp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
public class AppCheckFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Firebase-AppCheck";

    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/v1/orders",
            "/api/v1/wallet",
            "/api/v1/devices"
    );

    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/api/v1/auth",
            "/api/v1/categories",
            "/api/v1/catalog",
            "/api/v1/creators",
            "/api/v1/applications",
            "/api/v1/wallet/callback"
    );

    private final AppCheckTokenVerifier tokenVerifier;
    private final boolean enforced;

    public AppCheckFilter(AppCheckTokenVerifier tokenVerifier,
                          @Value("${app.appcheck.enforced:false}") boolean enforced) {
        this.tokenVerifier = tokenVerifier;
        this.enforced = enforced;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        for (String prefix : EXCLUDED_PREFIXES) {
            if (path.startsWith(prefix)) {
                chain.doFilter(request, response);
                return;
            }
        }

        boolean isProtected = PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
        if (!isProtected) {
            chain.doFilter(request, response);
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("[AppCheck] Firebase not initialized — skipping App Check for {}", path);
            chain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(HEADER);
        if (token == null || token.isBlank()) {
            log.warn("[AppCheck] Missing X-Firebase-AppCheck header for path={} enforced={}", path, enforced);
            if (enforced) {
                writeError(response, "APPCHECK_TOKEN_MISSING", "App Check token is required.");
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        try {
            String appId = tokenVerifier.verifyAndGetAppId(token);
            log.debug("[AppCheck] Token valid for path={} appId={}", path, appId);
        } catch (Exception e) {
            log.warn("[AppCheck] Token invalid for path={} enforced={} reason={}", path, enforced, e.getMessage());
            if (enforced) {
                writeError(response, "APPCHECK_TOKEN_INVALID", "App Check token is invalid or expired.");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
