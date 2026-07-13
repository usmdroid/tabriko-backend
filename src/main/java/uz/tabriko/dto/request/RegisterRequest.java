package uz.tabriko.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RegisterRequest {
    @NotBlank
    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Invalid phone number")
    private String phone;

    @NotBlank
    @Pattern(regexp = "^[0-9]{4,8}$", message = "Invalid OTP code")
    private String code;

    @NotBlank
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    private String name;

    @Email
    private String email;

    @Past
    private LocalDate birthDate;
}
