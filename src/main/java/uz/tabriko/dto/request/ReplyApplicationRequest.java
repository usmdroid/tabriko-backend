package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReplyApplicationRequest {

    @NotBlank
    @Size(max = 2000)
    private String text;

    @Size(max = 500)
    private String fileUrl;
}
