package uz.tabriko.dto.response;

import lombok.Data;

@Data
public class PromotionResponse {
    private Long id;
    private String title;
    private String subtitle;
    private String imageUrl;
    private String color;
    private Long categoryId;
    private String externalUrl;
}
