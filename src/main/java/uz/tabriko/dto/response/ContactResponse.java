package uz.tabriko.dto.response;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ContactResponse {
    private UUID id;
    private String phone;
    private String label;
    private Instant createdAt;
}
