package uz.tabriko.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import uz.tabriko.domain.enums.OrderOption;

import java.math.BigDecimal;
import java.util.Set;

@Data
public class UpdateCreatorProfileRequest {

    @Size(max = 1000)
    private String bio;

    private Long categoryId;

    @PositiveOrZero
    private BigDecimal priceFrom;

    @Min(1) @Max(3)
    private Integer deliveryDays;

    private Set<OrderOption> options;

    private Boolean accepting;
}
