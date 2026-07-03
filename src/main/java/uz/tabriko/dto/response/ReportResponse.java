package uz.tabriko.dto.response;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ReportResponse {
    private Long id;
    private UUID reporterId;
    private String targetType;
    private String targetId;
    private String reason;
    private String status;
    private Instant createdAt;
}
