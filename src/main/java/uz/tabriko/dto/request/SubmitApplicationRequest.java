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

    @NotBlank
    private String code;

    private String name;

    @NotNull
    private ApplicationActivityType activityType;

    private Long categoryId;

    private String otherText;

    @NotNull
    private ApplicationSocialType socialType;

    private String igUsername;

    private String sampleVideoUrl;
}
