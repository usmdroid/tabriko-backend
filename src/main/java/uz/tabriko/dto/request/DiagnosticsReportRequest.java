package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DiagnosticsReportRequest {

    @NotNull
    @Pattern(regexp = "CRITICAL|ERROR")
    private String level;

    @NotBlank
    @Size(max = 2000)
    private String message;

    private String stackTrace;
    private String platform;
    private String appVersion;
    private String osVersion;
    private String deviceModel;
    private String deviceId;
    private String screen;
    private OffsetDateTime occurredAt;
}
