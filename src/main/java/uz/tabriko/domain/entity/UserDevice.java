package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import uz.tabriko.domain.enums.Platform;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_devices")
@Getter
@Setter
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "fcm_token", nullable = false, length = 500)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Platform platform;

    @Column(name = "app_version", length = 20)
    private String appVersion;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Column(name = "os_version", length = 60)
    private String osVersion;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "rooted", nullable = false)
    private boolean rooted = false;

    @Column(name = "genuine")
    private Boolean genuine;

    @Column(name = "blocked", nullable = false)
    private boolean blocked = false;

    @Column(name = "attest_public_key", length = 2000)
    private String attestPublicKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
