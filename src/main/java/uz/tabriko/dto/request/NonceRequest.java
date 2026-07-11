package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NonceRequest {
    @NotBlank
    private String deviceId;
}
