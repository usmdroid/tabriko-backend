package uz.tabriko.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            "test-access-secret-must-be-long-enough-for-hs256",
            "test-refresh-secret-must-be-long-enough-for-hs256",
            3600000L,
            2592000000L
    );

    @Test
    void downloadToken_roundTrip_returnsFileUrlAndUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateDownloadToken("http://localhost:8080/files/media/x.mp4", userId, 60);

        JwtUtil.DownloadTokenClaims claims = jwtUtil.extractDownloadClaims(token);

        assertThat(claims).isNotNull();
        assertThat(claims.fileUrl()).isEqualTo("http://localhost:8080/files/media/x.mp4");
        assertThat(claims.userId()).isEqualTo(userId);
    }

    @Test
    void downloadToken_expired_returnsNull() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateDownloadToken("http://localhost:8080/files/media/x.mp4", userId, 0);
        Thread.sleep(5);

        assertThat(jwtUtil.extractDownloadClaims(token)).isNull();
    }

    @Test
    void downloadToken_tamperedSignature_returnsNull() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateDownloadToken("http://localhost:8080/files/media/x.mp4", userId, 60);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwtUtil.extractDownloadClaims(tampered)).isNull();
    }

    @Test
    void extractDownloadClaims_wrongTokenType_returnsNull() {
        // An access token has a different subject ("userId", not "download") and no url/uid claims
        String accessToken = jwtUtil.generateAccessToken(UUID.randomUUID(), "+998901234567", "CLIENT");

        assertThat(jwtUtil.extractDownloadClaims(accessToken)).isNull();
    }
}
