package uz.tabriko.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AdminOccasionResponse {
    private Long id;
    private String title;
    private LocalDate eventDate;
    private boolean recurringYearly;
    private String emoji;
    private String color;
    private String imageUrl;
    private Long categoryId;
    private boolean active;
    private int sortOrder;
    private LocalDateTime createdAt;
}
