package uz.tabriko.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import uz.tabriko.domain.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class UpdateCreatorServiceRequest {

    @NotNull
    @Positive
    private BigDecimal price;

    @NotNull
    @Min(1) @Max(3)
    private Integer deliveryDays;

    @NotNull
    private Boolean accepting;

    @NotNull
    private DiscountType discountType;

    private BigDecimal discountValue;

    private Instant discountStartsAt;

    private Instant discountEndsAt;
}
