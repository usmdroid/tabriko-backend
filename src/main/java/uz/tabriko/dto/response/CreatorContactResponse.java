package uz.tabriko.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class CreatorContactResponse {
    private UUID id;
    private String phone;
    private String label;
    private Instant createdAt;
}
