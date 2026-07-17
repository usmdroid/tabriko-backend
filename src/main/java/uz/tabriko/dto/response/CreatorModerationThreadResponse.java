package uz.tabriko.dto.response;

import lombok.Data;
import uz.tabriko.domain.enums.UserStatus;

import java.util.List;

@Data
public class CreatorModerationThreadResponse {
    private List<ModerationMessageResponse> messages;
    private UserStatus status;
    private String suspensionReason;
    private long unreadCount;
    private long activeWarningCount;
}
