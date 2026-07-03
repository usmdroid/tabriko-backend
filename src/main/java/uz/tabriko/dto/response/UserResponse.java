package uz.tabriko.dto.response;

import lombok.Data;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

@Data
public class UserResponse {
    private UUID id;
    private String phone;
    private String name;
    private String email;
    private Role role;
    private UserStatus status;
    private Instant createdAt;
}
