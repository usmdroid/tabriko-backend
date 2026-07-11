package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import uz.tabriko.domain.enums.BroadcastTargetType;
import uz.tabriko.domain.enums.Platform;
import uz.tabriko.dto.request.BroadcastNotificationRequest;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "broadcast_notifications")
@Getter
@Setter
public class BroadcastNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private BroadcastTargetType targetType;

    @Column(name = "min_version", length = 20)
    private String minVersion;

    @Column(name = "max_version", length = 20)
    private String maxVersion;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Platform platform;

    @Column(name = "user_count", nullable = false)
    private int userCount;

    @Column(name = "device_count", nullable = false)
    private int deviceCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static BroadcastNotification of(BroadcastNotificationRequest req, int userCount, int deviceCount) {
        BroadcastNotification bn = new BroadcastNotification();
        bn.title = req.getTitle();
        bn.body = req.getBody();
        bn.targetType = req.getTarget().getType();
        bn.minVersion = req.getTarget().getMinVersion();
        bn.maxVersion = req.getTarget().getMaxVersion();
        bn.platform = req.getTarget().getPlatform();
        bn.userCount = userCount;
        bn.deviceCount = deviceCount;
        return bn;
    }
}
