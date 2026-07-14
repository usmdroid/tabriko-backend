package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminRequisiteRequest {

    @NotBlank
    @Size(max = 60)
    private String name;

    private String emoji;

    private Boolean active;
}
