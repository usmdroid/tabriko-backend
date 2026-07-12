package uz.tabriko.infrastructure.media;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import uz.tabriko.security.JwtUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

// S3-compatible media storage; activate with STORAGE_PROVIDER=s3
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
@Slf4j
public class S3MediaStorageService implements MediaStorageService {

    @Value("${S3_ENDPOINT:}")
    private String endpoint;

    @Value("${S3_BUCKET}")
    private String bucket;

    @Value("${S3_REGION:us-east-1}")
    private String region;

    @Value("${S3_ACCESS_KEY}")
    private String accessKey;

    @Value("${S3_SECRET_KEY}")
    private String secretKey;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Autowired
    private JwtUtil jwtUtil;

    private S3Client s3;

    @PostConstruct
    private void init() {
        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);
        StaticCredentialsProvider provider = StaticCredentialsProvider.create(creds);
        Region awsRegion = Region.of(region);

        S3ClientBuilder clientBuilder = S3Client.builder()
            .region(awsRegion)
            .credentialsProvider(provider);

        if (!endpoint.isBlank()) {
            clientBuilder.endpointOverride(URI.create(endpoint));
        }

        s3 = clientBuilder.build();
    }

    @Override
    public String store(MultipartFile file, String folder) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String key = folder + "/" + UUID.randomUUID() + "." + ext;
            s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType(file.getContentType()).build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
            log.info("[S3] Stored: {}/{}", bucket, key);
            return "s3://" + bucket + "/" + key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    @Override
    public String applyWatermark(String mediaUrl) {
        // Deprecated: watermarking removed; kept for interface compatibility
        return mediaUrl;
    }

    @Override
    public String signedUrl(String mediaUrl, UUID userId, long ttlSeconds) {
        // Route through the /media/signed JWT endpoint so userId ownership is enforced
        // at serve time, identical to LocalMediaStorageService. The endpoint calls read()
        // which fetches the object directly via S3Client — no S3 presigning needed here.
        String token = jwtUtil.generateDownloadToken(mediaUrl, userId, ttlSeconds);
        return appBaseUrl + "/api/v1/media/signed?token=" + token;
    }

    @Override
    public InputStream read(String mediaUrl) {
        String key = mediaUrl.replaceFirst("s3://[^/]+/", "");
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
