package uz.tabriko.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateProfileRequest {
    @Size(min = 2, max = 100)
    private String name;

    @Email
    private String email;

    private LocalDate birthDate;

    // null = no change; true/false toggles whether the birthday is shared in
    // contacts birthday matches.
    private Boolean birthdayVisible;
}
