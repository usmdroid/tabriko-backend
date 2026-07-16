package uz.tabriko.telegram.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A Telegram chat that has started the crash-alert bot. Critical crash alerts
 * are broadcast to every subscriber. Populated by polling the alert bot's
 * getUpdates for /start (and any) messages.
 */
@Entity
@Table(name = "telegram_alert_subscriber")
@Getter
@Setter
public class TelegramAlertSubscriber {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Column(length = 100)
    private String username;

    @Column(name = "first_name", length = 200)
    private String firstName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
