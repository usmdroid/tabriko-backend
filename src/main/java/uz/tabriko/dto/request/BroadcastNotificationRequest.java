package uz.tabriko.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BroadcastNotificationRequest {
    @NotBlank
    private String title;
    @NotBlank
    private String body;
    @Valid
    @NotNull
    private BroadcastTarget target;
}
