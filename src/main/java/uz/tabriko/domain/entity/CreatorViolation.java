package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import uz.tabriko.domain.enums.ViolationType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_violation")
@Getter
@Setter
public class CreatorViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ViolationType type;

    @Column(nullable = false)
    private int severity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
