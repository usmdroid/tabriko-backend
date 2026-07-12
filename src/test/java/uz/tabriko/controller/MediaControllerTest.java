package uz.tabriko.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.infrastructure.media.MediaStorageService;
import uz.tabriko.security.JwtUtil;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.MediaService;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    @Mock MediaService mediaService;
    @Mock JwtUtil jwtUtil;
    @Mock MediaStorageService mediaStorage;

    @InjectMocks MediaController controller;

    @Test
    void streamSigned_invalidOrExpiredToken_unauthorized() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "+998901234567", "CLIENT");
        when(jwtUtil.extractDownloadClaims("bad-token")).thenReturn(null);

        assertThatThrownBy(() -> controller.streamSigned(principal, "bad-token"))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(401));
    }

    @Test
    void streamSigned_tokenBelongsToDifferentUser_forbidden() {
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        UserPrincipal stranger = new UserPrincipal(strangerId, "+998900000000", "CLIENT");

        when(jwtUtil.extractDownloadClaims("token"))
            .thenReturn(new JwtUtil.DownloadTokenClaims("http://localhost:8080/files/media/x.mp4", ownerId));

        assertThatThrownBy(() -> controller.streamSigned(stranger, "token"))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    void expiredOrForeignTokenIsRejected() {
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();

        // Expired / invalid token → null claims → 401
        UserPrincipal anyone = new UserPrincipal(ownerId, "+998901234567", "CLIENT");
        when(jwtUtil.extractDownloadClaims("expired-token")).thenReturn(null);
        assertThatThrownBy(() -> controller.streamSigned(anyone, "expired-token"))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(401));

        // Valid token but belongs to a different user → 403
        UserPrincipal stranger = new UserPrincipal(strangerId, "+998900000000", "CLIENT");
        when(jwtUtil.extractDownloadClaims("foreign-token"))
            .thenReturn(new JwtUtil.DownloadTokenClaims("http://localhost:8080/files/media/x.mp4", ownerId));
        assertThatThrownBy(() -> controller.streamSigned(stranger, "foreign-token"))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    void streamSigned_ownerOfToken_streamsFile() {
        UUID ownerId = UUID.randomUUID();
        UserPrincipal owner = new UserPrincipal(ownerId, "+998901234567", "CLIENT");

        when(jwtUtil.extractDownloadClaims("token"))
            .thenReturn(new JwtUtil.DownloadTokenClaims("http://localhost:8080/files/media/x.mp4", ownerId));
        when(mediaStorage.read("http://localhost:8080/files/media/x.mp4"))
            .thenReturn(new ByteArrayInputStream("video-bytes".getBytes()));

        ResponseEntity<?> response = controller.streamSigned(owner, "token");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
    }
}
