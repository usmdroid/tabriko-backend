package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminApplicationDecisionRequest {

    @NotBlank
    @Size(max = 1000)
    private String message;
}
