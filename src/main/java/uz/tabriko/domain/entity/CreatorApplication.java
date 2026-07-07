package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import uz.tabriko.domain.enums.ApplicationActivityType;
import uz.tabriko.domain.enums.ApplicationSocialType;
import uz.tabriko.domain.enums.ApplicationStatus;
import uz.tabriko.telegram.entity.TelegramVerification;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_applications")
@Getter
@Setter
public class CreatorApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 20)
    private ApplicationActivityType activityType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "other_text", length = 255)
    private String otherText;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_type", nullable = false, length = 20)
    private ApplicationSocialType socialType;

    @Column(name = "ig_username", length = 100)
    private String igUsername;

    @Column(name = "ig_verify_code", length = 255)
    private String igVerifyCode;

    @Column(name = "ig_ownership_confirmed", nullable = false)
    private boolean igOwnershipConfirmed = false;

    // Applicant-entered Telegram channel/group username, for reference only —
    // actual verification happens via the bot conversation (TelegramVerification).
    @Column(name = "telegram_username", length = 100)
    private String telegramUsername;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "telegram_verification_id")
    private TelegramVerification telegramVerification;

    @Column(name = "sample_video_url", length = 500)
    private String sampleVideoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "tracking_token", nullable = false, unique = true, length = 64)
    private String trackingToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
