package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FlagCreatorRequest {

    /** Accepted values: "top" or "exclusive" */
    @NotBlank
    private String flag;
}
