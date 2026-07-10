package uz.tabriko.infrastructure.media;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uz.tabriko.security.JwtUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Pattern;

// Local-disk media storage for dev; set STORAGE_PROVIDER=s3 to use S3 in production
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalMediaStorageService implements MediaStorageService {

    @Value("${app.media.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.media.base-url:http://localhost:8080/files}")
    private String baseUrl;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public String store(MultipartFile file, String folder) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + "." + ext;
            Path dir = Paths.get(uploadDir, folder);
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename));
            return baseUrl + "/" + folder + "/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    @Override
    public String applyWatermark(String mediaUrl) {
        // Stub: in production apply actual watermark; here return same URL tagged
        log.info("[MEDIA] Watermarking: {}", mediaUrl);
        return mediaUrl + "?watermarked=true";
    }

    @Override
    public String signedUrl(String mediaUrl, UUID userId, long ttlSeconds) {
        // Generate a short-lived JWT embedding the clean file URL and owning userId; the
        // caller must present a valid JWT session matching that userId to stream the file.
        String token = jwtUtil.generateDownloadToken(mediaUrl, userId, ttlSeconds);
        return appBaseUrl + "/api/v1/media/signed?token=" + token;
    }

    @Override
    public InputStream read(String mediaUrl) {
        try {
            String withoutQuery = mediaUrl.replaceFirst("\\?.*$", "");
            String relative = withoutQuery.replaceFirst("^" + Pattern.quote(baseUrl) + "/", "");
            return Files.newInputStream(Paths.get(uploadDir, relative));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
