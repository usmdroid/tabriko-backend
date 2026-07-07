package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminOccasionRequest {

    @NotBlank
    private String title;

    @NotNull
    private LocalDate eventDate;

    private boolean recurringYearly;

    private String emoji;

    private String color;

    private String imageUrl;

    private Long categoryId;

    private boolean active = true;

    private int sortOrder = 0;
}
