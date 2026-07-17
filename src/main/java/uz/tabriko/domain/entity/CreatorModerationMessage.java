package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import uz.tabriko.domain.enums.ModerationAuthorRole;
import uz.tabriko.domain.enums.ModerationMessageKind;

import java.time.Instant;

@Entity
@Table(name = "creator_moderation_message")
@Getter
@Setter
public class CreatorModerationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_user_id", nullable = false)
    private User creatorUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 20)
    private ModerationAuthorRole authorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationMessageKind kind;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_by_creator", nullable = false)
    private boolean readByCreator = false;
}
