package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddCreatorContactRequest {

    @NotBlank
    @Pattern(regexp = "^\\+?[0-9]{9,15}$")
    private String phone;

    @Size(max = 100)
    private String label;
}
