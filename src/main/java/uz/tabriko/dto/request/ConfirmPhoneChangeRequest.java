package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ConfirmPhoneChangeRequest {
    @NotBlank
    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Invalid phone number")
    private String newPhone;

    @NotBlank
    @Pattern(regexp = "^[0-9]{4,8}$", message = "Invalid OTP code")
    private String code;
}
