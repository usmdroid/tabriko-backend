package uz.tabriko.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PatchRequisiteRequest {

    @Size(max = 60)
    private String name;

    private String emoji;

    private Boolean active;

    @DecimalMin("0")
    private BigDecimal price;
}
