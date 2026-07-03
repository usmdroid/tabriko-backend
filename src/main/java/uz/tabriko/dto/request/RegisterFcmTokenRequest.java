package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterFcmTokenRequest {
    @NotBlank
    private String token;
}
