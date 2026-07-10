package uz.tabriko.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtUtil {

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final long accessExpiryMs;
    private final long refreshExpiryMs;

    public JwtUtil(
            @Value("${app.jwt.access-secret}") String accessSecret,
            @Value("${app.jwt.refresh-secret}") String refreshSecret,
            @Value("${app.jwt.access-expiry-ms}") long accessExpiryMs,
            @Value("${app.jwt.refresh-expiry-ms}") long refreshExpiryMs
    ) {
        this.accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiryMs = accessExpiryMs;
        this.refreshExpiryMs = refreshExpiryMs;
    }

    public String generateAccessToken(UUID userId, String phone, String role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("phone", phone)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiryMs))
                .signWith(accessKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiryMs))
                .signWith(refreshKey)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parser().verifyWith(accessKey).build()
                .parseSignedClaims(token).getPayload();
    }

    public Claims parseRefreshToken(String token) {
        return Jwts.parser().verifyWith(refreshKey).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean isAccessTokenValid(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid access token: {}", e.getMessage());
            return false;
        }
    }

    // Short-lived download token for signed media URLs (local storage). Bound to the
    // requesting user so the token cannot be reused by anyone other than its owner.
    public String generateDownloadToken(String fileUrl, UUID userId, long ttlSeconds) {
        return Jwts.builder()
            .subject("download")
            .claim("url", fileUrl)
            .claim("uid", userId.toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + ttlSeconds * 1000L))
            .signWith(accessKey)
            .compact();
    }

    public DownloadTokenClaims extractDownloadClaims(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(accessKey).build()
                .parseSignedClaims(token).getPayload();
            if (!"download".equals(claims.getSubject())) {
                return null;
            }
            String url = claims.get("url", String.class);
            String uid = claims.get("uid", String.class);
            if (url == null || uid == null) {
                return null;
            }
            return new DownloadTokenClaims(url, UUID.fromString(uid));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid download token: {}", e.getMessage());
            return null;
        }
    }

    public record DownloadTokenClaims(String fileUrl, UUID userId) {
    }
}
