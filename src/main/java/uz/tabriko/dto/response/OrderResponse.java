package uz.tabriko.dto.response;

import lombok.Data;
import uz.tabriko.domain.enums.OrderOption;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.domain.enums.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class OrderResponse {
    private UUID id;
    private UUID clientId;
    private String clientName;
    private String clientPhone;
    private UUID creatorId;
    private String creatorName;
    private String creatorPhone;
    private OrderType type;
    private OrderOption option;
    private String recipientName;
    private String recipientOccasion;
    private String customText;
    private boolean isPublic;
    private BigDecimal price;
    private OrderStatus status;
    private Instant deadline;
    private Instant createdAt;
    private String rejectionReason;
    private DeliveryResponse delivery;
}
