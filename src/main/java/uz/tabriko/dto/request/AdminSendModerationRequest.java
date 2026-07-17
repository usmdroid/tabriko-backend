package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import uz.tabriko.domain.enums.ModerationMessageKind;

@Data
public class AdminSendModerationRequest {
    @NotNull
    private ModerationMessageKind kind;

    @NotBlank
    @Size(max = 2000)
    private String body;
}
