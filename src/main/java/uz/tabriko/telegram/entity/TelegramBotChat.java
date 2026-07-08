package uz.tabriko.telegram.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Every chat/channel the bot is currently an administrator of, recorded as soon as
 * Telegram reports the promotion (my_chat_member) — independent of whether the promoter
 * has already linked their phone. Telegram's Bot API has no "list my admin chats" method,
 * so this table is the only way to later reconcile "which chat did this creator add me to".
 */
@Entity
@Table(name = "telegram_bot_chats")
@Getter
@Setter
public class TelegramBotChat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "chat_id", nullable = false, unique = true)
    private Long chatId;

    @Column(name = "chat_username", length = 100)
    private String chatUsername;

    @Column(name = "chat_title", length = 255)
    private String chatTitle;

    @Column(name = "chat_type", length = 30)
    private String chatType;

    @Column(name = "subscribers")
    private Integer subscribers;

    @Column(name = "owner_status", length = 20)
    private String ownerStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
