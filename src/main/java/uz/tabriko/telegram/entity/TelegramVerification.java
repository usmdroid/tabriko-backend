package uz.tabriko.telegram.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import uz.tabriko.telegram.enums.TelegramChatType;
import uz.tabriko.telegram.enums.TelegramOwnerStatus;
import uz.tabriko.telegram.enums.TelegramVerificationStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telegram_verification")
@Getter
@Setter
public class TelegramVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "chat_username", length = 100)
    private String chatUsername;

    @Column(name = "chat_title", length = 255)
    private String chatTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_type", length = 30)
    private TelegramChatType chatType;

    @Column(name = "subscribers")
    private Integer subscribers;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_status", length = 20)
    private TelegramOwnerStatus ownerStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TelegramVerificationStatus status;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
