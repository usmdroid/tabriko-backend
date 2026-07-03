package uz.tabriko.infrastructure.media;

import org.springframework.web.multipart.MultipartFile;

public interface MediaStorageService {
    String store(MultipartFile file, String folder);
    String applyWatermark(String mediaUrl);
    // Returns a short-lived URL granting read access to the clean file
    String signedUrl(String mediaUrl, long ttlSeconds);
}
