package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import uz.tabriko.domain.enums.OrderOption;
import uz.tabriko.domain.enums.OrderType;

import java.util.List;
import java.util.UUID;

@Data
public class CreateOrderRequest {
    @NotNull
    private UUID creatorId;

    @NotNull
    private OrderType type;

    private OrderOption option;

    private String recipientName;

    private String recipientOccasion;

    private String customText;

    private boolean isPublic = false;

    private List<Long> requisiteIds;
}
