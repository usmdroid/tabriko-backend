package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminPromotionRequest {

    @NotBlank
    private String title;

    private String subtitle;

    private String imageUrl;

    private String color;

    private Long categoryId;

    private String externalUrl;

    private boolean active = true;

    private int sortOrder = 0;
}
