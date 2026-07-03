package uz.tabriko.dto.response;

import lombok.Data;

import java.util.UUID;

@Data
public class PortfolioItemResponse {
    private Long id;
    private String mediaUrl;
    private boolean isPublic;
    private UUID orderId;
}
