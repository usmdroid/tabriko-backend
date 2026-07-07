package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import uz.tabriko.domain.enums.ApplicationActivityType;
import uz.tabriko.domain.enums.ApplicationSocialType;

@Data
public class SubmitApplicationRequest {

    @NotBlank
    private String phone;

    // Issued by POST /applications/verify-phone after the OTP is confirmed —
    // decouples the (short-lived) OTP from filling out the rest of the form.
    @NotBlank
    private String verifyToken;

    @NotBlank
    private String name;

    @NotNull
    private ApplicationActivityType activityType;

    private Long categoryId;

    private String otherText;

    @NotNull
    private ApplicationSocialType socialType;

    private String igUsername;

    // Applicant-entered Telegram channel/group username (reference only).
    private String telegramUsername;

    private String sampleVideoUrl;
}
