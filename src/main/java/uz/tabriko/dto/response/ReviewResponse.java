package uz.tabriko.dto.response;

import lombok.Data;

import java.time.Instant;

@Data
public class ReviewResponse {
    private Long id;
    private String clientName;
    private int stars;
    private String comment;
    private Instant createdAt;
}
