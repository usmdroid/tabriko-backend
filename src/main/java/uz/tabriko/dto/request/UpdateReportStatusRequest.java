package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import uz.tabriko.domain.enums.ReportStatus;

@Data
public class UpdateReportStatusRequest {
    @NotNull
    private ReportStatus status;
}
