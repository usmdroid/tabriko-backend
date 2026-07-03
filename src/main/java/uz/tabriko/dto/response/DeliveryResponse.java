package uz.tabriko.dto.response;

import lombok.Data;

import java.time.Instant;

@Data
public class DeliveryResponse {
    private Long id;
    private String mediaUrl;
    private boolean watermarked;
    private Instant deliveredAt;
}
