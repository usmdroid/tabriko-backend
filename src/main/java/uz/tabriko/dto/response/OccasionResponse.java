package uz.tabriko.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class OccasionResponse {
    private Long id;
    private String title;
    private LocalDate date;
    private int daysLeft;
    private String emoji;
    private String color;
    private String imageUrl;
    private Long categoryId;
}
