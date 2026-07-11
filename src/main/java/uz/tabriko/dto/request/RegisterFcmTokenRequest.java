package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import uz.tabriko.domain.enums.Platform;

@Data
public class RegisterFcmTokenRequest {
    @NotBlank
    private String token;

    @NotNull
    private Platform platform;

    @NotBlank
    private String appVersion;

    private String deviceName;
    private String osVersion;
}
