package uz.tabriko.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminPromotionResponse {
    private Long id;
    private String title;
    private String subtitle;
    private String imageUrl;
    private String color;
    private Long categoryId;
    private String externalUrl;
    private boolean active;
    private int sortOrder;
    private LocalDateTime createdAt;
}
