package uz.tabriko.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import uz.tabriko.domain.enums.OrderType;

import java.math.BigDecimal;

@Data
public class AddCreatorRequisiteRequest {

    @NotNull
    private OrderType serviceType;

    private Long catalogId;

    @Size(max = 60)
    private String customName;

    @DecimalMin("0")
    private BigDecimal price;
}
