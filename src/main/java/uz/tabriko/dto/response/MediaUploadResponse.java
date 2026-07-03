package uz.tabriko.dto.response;

import lombok.Data;

import java.util.UUID;

@Data
public class MediaUploadResponse {
    private UUID orderId;
    private String watermarkedUrl;
}
