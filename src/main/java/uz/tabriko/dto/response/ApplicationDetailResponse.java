package uz.tabriko.dto.response;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class ApplicationDetailResponse {
    private UUID id;
    private String phone;
    private String name;
    private String activityType;
    private Long categoryId;
    private String categoryName;
    private String otherText;
    private String passportSeries;
    private String passportNumber;
    private List<String> socialTypes;
    private String igUsername;
    private String igVerifyCode;
    private boolean igOwnershipConfirmed;
    private String telegramUsername;
    private UUID telegramVerificationId;
    private String sampleVideoUrl;
    private String status;
    private String decisionReason;
    private String trackingToken;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ApplicationMessageResponse> messages;
    private ApplicationVerificationResponse verification;
}
