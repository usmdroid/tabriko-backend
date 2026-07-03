package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePortfolioVisibilityRequest {
    @NotNull
    private Boolean isPublic;
}
