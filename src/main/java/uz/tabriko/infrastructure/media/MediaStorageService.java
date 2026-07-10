package uz.tabriko.infrastructure.media;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

public interface MediaStorageService {
    String store(MultipartFile file, String folder);
    String applyWatermark(String mediaUrl);
    // Returns a short-lived URL granting read access to the clean file, bound to userId
    String signedUrl(String mediaUrl, UUID userId, long ttlSeconds);
    // Reads the raw bytes for a stored mediaUrl (used by the authenticated download stream)
    InputStream read(String mediaUrl);
}
