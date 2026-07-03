package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import uz.tabriko.domain.enums.ReportTargetType;

@Data
public class CreateReportRequest {

    @NotNull
    private ReportTargetType targetType;

    @NotBlank @Size(max = 100)
    private String targetId;

    @NotBlank @Size(max = 2000)
    private String reason;
}
