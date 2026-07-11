package uz.tabriko.security;

import com.google.firebase.FirebaseApp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AppCheckFilterTest {

    private AppCheckTokenVerifier verifier;
    private HttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        verifier = mock(AppCheckTokenVerifier.class);
        request = mock(HttpServletRequest.class);
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @Test
    void unprotectedPath_passesThrough() throws Exception {
        AppCheckFilter filter = new AppCheckFilter(verifier, false);
        when(request.getRequestURI()).thenReturn("/api/v1/profile");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(verifier);
    }

    @Test
    void excludedPath_passesThrough() throws Exception {
        AppCheckFilter filter = new AppCheckFilter(verifier, false);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(verifier);
    }

    @Test
    void firebaseNotInitialized_passesThrough() throws Exception {
        AppCheckFilter filter = new AppCheckFilter(verifier, false);
        when(request.getRequestURI()).thenReturn("/api/v1/orders/123");

        try (MockedStatic<FirebaseApp> appMock = mockStatic(FirebaseApp.class)) {
            appMock.when(FirebaseApp::getApps).thenReturn(List.of());

            filter.doFilterInternal(request, response, chain);
        }

        verify(chain).doFilter(request, response);
        verifyNoInteractions(verifier);
    }

    @Test
    void tokenMissing_enforcedFalse_passesThrough() throws Exception {
        AppCheckFilter filter = new AppCheckFilter(verifier, false);
        when(request.getRequestURI()).thenReturn("/api/v1/orders/123");
        when(request.getHeader("X-Firebase-AppCheck")).thenReturn(null);

        try (MockedStatic<FirebaseApp> appMock = mockStatic(FirebaseApp.class)) {
            appMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));

            filter.doFilterInternal(request, response, chain);
        }

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void tokenMissing_enforcedTrue_returns403() throws Exception {
        AppCheckFilter filter = new AppCheckFilter(verifier, true);
        when(request.getRequestURI()).thenReturn("/api/v1/orders/123");
        when(request.getHeader("X-Firebase-AppCheck")).thenReturn(null);

        try (MockedStatic<FirebaseApp> appMock = mockStatic(FirebaseApp.class)) {
            appMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));

            filter.doFilterInternal(request, response, chain);
        }

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("APPCHECK_TOKEN_MISSING");
    }

    @Test
    void tokenValid_passesThrough() throws Exception {
        AppCheckFilter filter = new AppCheckFilter(verifier, false);
        when(request.getRequestURI()).thenReturn("/api/v1/orders/123");
        when(request.getHeader("X-Firebase-AppCheck")).thenReturn("valid-token");
        when(verifier.verifyAndGetAppId("valid-token")).thenReturn("1:123456:android:abc");

        try (MockedStatic<FirebaseApp> appMock = mockStatic(FirebaseApp.class)) {
            appMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));

            filter.doFilterInternal(request, response, chain);
        }

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void tokenInvalid_enforcedFalse_passesThrough() throws Exception {
        AppCheckFilter filter = new AppCheckFilter(verifier, false);
        when(request.getRequestURI()).thenReturn("/api/v1/orders/123");
        when(request.getHeader("X-Firebase-AppCheck")).thenReturn("bad-token");
        when(verifier.verifyAndGetAppId("bad-token")).thenThrow(new RuntimeException("Invalid signature"));

        try (MockedStatic<FirebaseApp> appMock = mockStatic(FirebaseApp.class)) {
            appMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));

            filter.doFilterInternal(request, response, chain);
        }

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void tokenInvalid_enforcedTrue_returns403() throws Exception {
        AppCheckFilter filter = new AppCheckFilter(verifier, true);
        when(request.getRequestURI()).thenReturn("/api/v1/orders/123");
        when(request.getHeader("X-Firebase-AppCheck")).thenReturn("bad-token");
        when(verifier.verifyAndGetAppId("bad-token")).thenThrow(new RuntimeException("Invalid signature"));

        try (MockedStatic<FirebaseApp> appMock = mockStatic(FirebaseApp.class)) {
            appMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));

            filter.doFilterInternal(request, response, chain);
        }

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("APPCHECK_TOKEN_INVALID");
    }

    @Test
    void walletCallback_excludedEvenWhenEnforced_passesThrough() throws Exception {
        AppCheckFilter filter = new AppCheckFilter(verifier, true);
        when(request.getRequestURI()).thenReturn("/api/v1/wallet/callback");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(verifier);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void tokenInvalid_wrongIssuer_enforcedTrue_returns403() throws Exception {
        AppCheckFilter filter = new AppCheckFilter(verifier, true);
        when(request.getRequestURI()).thenReturn("/api/v1/wallet/balance");
        when(request.getHeader("X-Firebase-AppCheck")).thenReturn("wrong-project-token");
        when(verifier.verifyAndGetAppId("wrong-project-token"))
                .thenThrow(new RuntimeException("JWT iss claim mismatch"));

        try (MockedStatic<FirebaseApp> appMock = mockStatic(FirebaseApp.class)) {
            appMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));

            filter.doFilterInternal(request, response, chain);
        }

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("APPCHECK_TOKEN_INVALID");
    }
}
