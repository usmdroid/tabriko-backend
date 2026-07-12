package uz.tabriko.infrastructure.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uz.tabriko.security.JwtUtil;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class S3MediaStorageServiceTest {

    private S3MediaStorageService service;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        service = new S3MediaStorageService();
        jwtUtil = mock(JwtUtil.class);

        ReflectionTestUtils.setField(service, "appBaseUrl", "https://api.tabriko.uz");
        ReflectionTestUtils.setField(service, "jwtUtil", jwtUtil);
    }

    @Test
    void signedUrl_returnsMediaSignedEndpointUrl_notDirectS3Url() {
        UUID userId = UUID.randomUUID();
        String mediaUrl = "s3://my-bucket/media/abc.mp4";

        when(jwtUtil.generateDownloadToken(mediaUrl, userId, 3600L)).thenReturn("test.jwt.token");

        String result = service.signedUrl(mediaUrl, userId, 3600L);

        assertThat(result).startsWith("https://api.tabriko.uz/api/v1/media/signed?token=");
        assertThat(result).contains("test.jwt.token");
        assertThat(result).doesNotContain("s3.amazonaws.com");
        assertThat(result).doesNotContain("X-Amz-Signature");
        verify(jwtUtil).generateDownloadToken(mediaUrl, userId, 3600L);
    }

    @Test
    void signedUrl_passesCorrectTtlToJwt() {
        UUID userId = UUID.randomUUID();
        String mediaUrl = "s3://my-bucket/media/vid.mp4";
        long ttl = 7200L;

        when(jwtUtil.generateDownloadToken(mediaUrl, userId, ttl)).thenReturn("another.token");

        service.signedUrl(mediaUrl, userId, ttl);

        verify(jwtUtil).generateDownloadToken(mediaUrl, userId, ttl);
    }
}
