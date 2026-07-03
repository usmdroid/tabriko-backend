package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddCreatorRequest {
    @NotBlank
    @Pattern(regexp = "^\\+?[0-9]{9,15}$")
    private String phone;

    @NotBlank
    private String name;

    @NotNull
    private Long categoryId;

    private String bio;

    private BigDecimal priceFrom;

    private int deliveryDays = 3;
}
