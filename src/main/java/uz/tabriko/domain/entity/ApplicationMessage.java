package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import uz.tabriko.domain.enums.MessageAuthor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_messages")
@Getter
@Setter
public class ApplicationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private CreatorApplication application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageAuthor author;

    @Column(length = 2000)
    private String text;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
