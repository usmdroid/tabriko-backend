package uz.tabriko.dto.response;

import java.time.Instant;
import java.util.UUID;

/** One row of the admin "deleted accounts" audit tab. */
public record DeletedAccountResponse(
        UUID id,
        String name,
        String phone,
        String role,
        Instant deletedAt,
        String reason,
        String deletedByName
) {
}
