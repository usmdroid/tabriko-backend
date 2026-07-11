package uz.tabriko.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import uz.tabriko.domain.enums.BroadcastTargetType;
import uz.tabriko.domain.enums.Platform;

@Data
public class BroadcastTarget {
    @NotNull
    private BroadcastTargetType type;
    private String minVersion;
    private String maxVersion;
    private Platform platform;
}
