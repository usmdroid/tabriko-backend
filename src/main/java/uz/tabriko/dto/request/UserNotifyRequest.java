package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UserNotifyRequest {
    @NotBlank
    private String title;

    @NotBlank
    private String body;

    private List<UUID> deviceIds;
}
