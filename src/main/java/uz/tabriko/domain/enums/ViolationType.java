package uz.tabriko.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ViolationType {
    DELIVERY_FAILURE(5),
    REJECTION(4),
    NO_RESPONSE(3);

    private final int severity;
}
