package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import uz.tabriko.domain.enums.OrderOption;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.domain.enums.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderOption option;

    // Recipient info
    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_occasion", length = 200)
    private String recipientOccasion;

    @Column(name = "custom_text", length = 2000)
    private String customText;

    // Whether this is a gift (private) or public
    @Column(name = "is_public")
    private boolean isPublic = false;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false)
    private Instant deadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "flagged", nullable = false)
    private boolean flagged = false;

    // Client grants creator permission to show delivery in portfolio
    @Column(name = "portfolio_consent", nullable = false)
    private boolean portfolioConsent = false;
}
