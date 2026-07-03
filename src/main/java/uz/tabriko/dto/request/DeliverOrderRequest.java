package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeliverOrderRequest {
    // URL returned by media upload endpoint
    @NotBlank
    private String mediaUrl;
}
