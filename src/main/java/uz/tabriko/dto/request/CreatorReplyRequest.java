package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatorReplyRequest {
    @NotBlank
    @Size(max = 2000)
    private String body;
}
