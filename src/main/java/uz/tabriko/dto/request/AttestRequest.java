package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import uz.tabriko.domain.enums.Platform;

@Data
public class AttestRequest {
    @NotBlank
    private String deviceId;

    @NotNull
    private Platform platform;

    @NotBlank
    private String integrityToken;

    @NotBlank
    private String nonce;
}
