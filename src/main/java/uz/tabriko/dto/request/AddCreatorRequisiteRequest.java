package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import uz.tabriko.domain.enums.OrderType;

@Data
public class AddCreatorRequisiteRequest {

    @NotNull
    private OrderType serviceType;

    private Long catalogId;

    @Size(max = 60)
    private String customName;
}
