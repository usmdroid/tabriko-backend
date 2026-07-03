package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateConsentRequest {
    @NotNull
    private Boolean portfolioConsent;
}
