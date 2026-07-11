package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddContactRequest {
    @NotBlank
    private String phone;
    private String label;
}
