package uz.tabriko.dto.response;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class AdminUserResponse {
    private UUID id;
    private String name;
    private String phone;
    private String status; // "active" or "blocked" (lowercased UserStatus)
    private Instant createdAt;
}
