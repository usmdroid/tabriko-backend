package uz.tabriko.dto.response;

import lombok.Data;

import java.time.Instant;

@Data
public class AdminRequisiteResponse {
    private Long id;
    private String name;
    private String emoji;
    private boolean active;
    private Instant createdAt;
}
