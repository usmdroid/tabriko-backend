package uz.tabriko.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ProtectedHeader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class AppCheckTokenVerifier {

    private static final String JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks";
    private static final long CACHE_TTL_MS = 3_600_000L;

    private final String expectedIssuer;
    private final String expectedAudience;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile Map<String, PublicKey> keyCache = Map.of();
    private volatile long cacheExpiresAt = 0;

    public AppCheckTokenVerifier(@Value("${app.appcheck.project-number}") String projectNumber) {
        this.expectedIssuer = "https://firebaseappcheck.googleapis.com/" + projectNumber;
        this.expectedAudience = "projects/" + projectNumber;
    }

    public String verifyAndGetAppId(String token) throws Exception {
        Claims claims = Jwts.parser()
                .keyLocator(header -> {
                    if (!(header instanceof ProtectedHeader ph)) {
                        throw new RuntimeException("JWT header is not a protected header");
                    }
                    String kid = ph.getKeyId();
                    if (kid == null) {
                        throw new RuntimeException("JWT header missing kid");
                    }
                    try {
                        return getPublicKey(kid);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to resolve key for kid=" + kid, e);
                    }
                })
                .requireIssuer(expectedIssuer)
                .requireAudience(expectedAudience)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String appId = claims.get("app_id", String.class);
        return appId != null ? appId : claims.getSubject();
    }

    private PublicKey getPublicKey(String kid) throws Exception {
        if (System.currentTimeMillis() < cacheExpiresAt && keyCache.containsKey(kid)) {
            return keyCache.get(kid);
        }
        refreshKeys();
        PublicKey key = keyCache.get(kid);
        if (key == null) {
            throw new IllegalArgumentException("Unknown kid: " + kid);
        }
        return key;
    }

    private synchronized void refreshKeys() throws Exception {
        if (System.currentTimeMillis() < cacheExpiresAt) {
            return;
        }
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(JWKS_URL)).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        @SuppressWarnings("unchecked")
        Map<String, Object> jwks = objectMapper.readValue(resp.body(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");

        Map<String, PublicKey> fresh = new ConcurrentHashMap<>();
        KeyFactory kf = KeyFactory.getInstance("RSA");
        for (Map<String, Object> k : keys) {
            String id = (String) k.get("kid");
            byte[] n = Base64.getUrlDecoder().decode((String) k.get("n"));
            byte[] e = Base64.getUrlDecoder().decode((String) k.get("e"));
            fresh.put(id, kf.generatePublic(new RSAPublicKeySpec(new BigInteger(1, n), new BigInteger(1, e))));
        }
        keyCache = fresh;
        cacheExpiresAt = System.currentTimeMillis() + CACHE_TTL_MS;
        log.debug("[AppCheck] JWKS refreshed, {} keys loaded", fresh.size());
    }
}
