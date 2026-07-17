package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SuspendCreatorRequest {

    @NotBlank
    private String reason;
}
