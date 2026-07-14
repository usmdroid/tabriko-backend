package uz.tabriko.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatorRejectOrderRequest {
    @Size(max = 500)
    private String rejectionReason;
}
