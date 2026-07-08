package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import uz.tabriko.domain.enums.ApplicationActivityType;
import uz.tabriko.domain.enums.ApplicationSocialType;

import java.util.Set;

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

    // Passport identity — series is 2 letters (e.g. AB), number is 7 digits.
    @NotBlank
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "Passport series must be 2 letters")
    private String passportSeries;

    @NotBlank
    @Pattern(regexp = "^[0-9]{7}$", message = "Passport number must be 7 digits")
    private String passportNumber;

    // One or both of TELEGRAM / INSTAGRAM — a creator may run several networks.
    @NotEmpty
    private Set<ApplicationSocialType> socialTypes;

    private String igUsername;

    // Applicant-entered Telegram channel/group username (reference only).
    private String telegramUsername;

    private String sampleVideoUrl;
}
