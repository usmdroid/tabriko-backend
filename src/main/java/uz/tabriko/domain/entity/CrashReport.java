package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "crash_report")
@Getter
@Setter
public class CrashReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String level;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(length = 50)
    private String platform;

    @Column(name = "app_version", length = 30)
    private String appVersion;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(length = 200)
    private String screen;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
