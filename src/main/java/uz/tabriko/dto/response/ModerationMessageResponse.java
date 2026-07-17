package uz.tabriko.dto.response;

import lombok.Data;
import uz.tabriko.domain.enums.ModerationAuthorRole;
import uz.tabriko.domain.enums.ModerationMessageKind;

import java.time.Instant;

@Data
public class ModerationMessageResponse {
    private Long id;
    private ModerationAuthorRole authorRole;
    private ModerationMessageKind kind;
    private String body;
    private Instant createdAt;
    private boolean readByCreator;
}
