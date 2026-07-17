package uz.tabriko.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminUserDetailResponse(
        UUID id,
        String name,
        String phone,
        String email,
        LocalDate birthDate,
        String role,
        String status,
        Instant createdAt,
        String accountNumber,
        String avatarUrl,
        List<DeviceSummary> devices
) {
    public record DeviceSummary(
            UUID id,
            String deviceId,
            String platform,
            String appVersion,
            String deviceName,
            String osVersion,
            Instant updatedAt,
            boolean rooted,
            Boolean genuine,
            boolean blocked
    ) {}
}
