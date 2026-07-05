package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Invalid phone number")
    private String phone;

    @NotBlank
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}
