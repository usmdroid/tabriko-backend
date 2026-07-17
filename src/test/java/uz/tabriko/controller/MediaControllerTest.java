package uz.tabriko.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import uz.tabriko.infrastructure.media.MediaStorageService;
import uz.tabriko.security.JwtUtil;
import uz.tabriko.service.MediaService;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    @Mock MediaService mediaService;
    @Mock JwtUtil jwtUtil;
    @Mock MediaStorageService mediaStorage;

    @InjectMocks MediaController controller;

    @Test
    void streamSigned_nullClaims_returns401() {
        when(jwtUtil.extractDownloadClaims("bad-token")).thenReturn(null);

        ResponseEntity<?> response = controller.streamSigned("bad-token");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void streamSigned_throwingExtract_returns401() {
        when(jwtUtil.extractDownloadClaims("bad-token")).thenThrow(new RuntimeException("expired"));

        ResponseEntity<?> response = controller.streamSigned("bad-token");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void streamSigned_validToken_noAuthHeader_streams200() {
        UUID ownerId = UUID.randomUUID();

        when(jwtUtil.extractDownloadClaims("token"))
            .thenReturn(new JwtUtil.DownloadTokenClaims("http://localhost:8080/files/media/x.mp4", ownerId));
        when(mediaStorage.read("http://localhost:8080/files/media/x.mp4"))
            .thenReturn(new ByteArrayInputStream("video-bytes".getBytes()));

        ResponseEntity<?> response = controller.streamSigned("token");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
    }
}
