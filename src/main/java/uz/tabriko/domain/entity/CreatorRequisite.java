package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import uz.tabriko.domain.enums.RequisiteSource;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_requisite")
@Getter
@Setter
public class CreatorRequisite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_user_id", nullable = false)
    private UUID creatorUserId;

    @Column(name = "catalog_id")
    private Long catalogId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 10)
    private String emoji;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RequisiteSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
