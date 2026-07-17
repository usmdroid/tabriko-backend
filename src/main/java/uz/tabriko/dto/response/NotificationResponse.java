package uz.tabriko.dto.response;

import lombok.Data;
import uz.tabriko.domain.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

@Data
public class NotificationResponse {
    private Long id;
    private String title;
    private String body;
    private NotificationType type;
    private boolean isRead;
    private Instant createdAt;
    private UUID orderId;
}
