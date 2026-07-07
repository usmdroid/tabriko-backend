package uz.tabriko.dto.response;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ApplicationMessageResponse {
    private UUID id;
    private String author;
    private String text;
    private String fileUrl;
    private Instant createdAt;
}
