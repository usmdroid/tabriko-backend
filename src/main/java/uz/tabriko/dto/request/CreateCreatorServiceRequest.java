package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import uz.tabriko.domain.enums.OrderType;

@Data
public class CreateCreatorServiceRequest {

    @NotNull
    private OrderType type;
}
