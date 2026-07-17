package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import uz.tabriko.domain.enums.CreatorTier;
import uz.tabriko.domain.enums.OrderOption;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "creator_profiles")
@Getter
@Setter
public class CreatorProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(length = 1000)
    private String bio;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "rating_count")
    private int ratingCount = 0;

    @Column(name = "price_from", precision = 12, scale = 2)
    private BigDecimal priceFrom = BigDecimal.ZERO;

    @Column(name = "delivery_days")
    private int deliveryDays = 3;

    @Column(name = "is_top")
    private boolean isTop = false;

    @Column(name = "is_exclusive")
    private boolean isExclusive = false;

    @Column(name = "is_verified")
    private boolean isVerified = false;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Column(name = "accepting", nullable = false)
    private boolean accepting = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 20, nullable = false)
    private CreatorTier tier = CreatorTier.STANDARD;

    @Column(name = "id_document_number")
    private String idDocumentNumber;

    @Column(name = "id_document_url", length = 500)
    private String idDocumentUrl;

    @Column(name = "payout_card")
    private String payoutCard;

    @Column(name = "payout_account")
    private String payoutAccount;

    @Column(name = "payout_holder")
    private String payoutHolder;

    @Column(name = "social_telegram")
    private String socialTelegram;

    @Column(name = "social_instagram")
    private String socialInstagram;

    @Column(name = "profile_complete", nullable = false)
    private boolean profileComplete = false;

    @Column(name = "public_code", unique = true, nullable = false, length = 20)
    private String publicCode;

    @Column(name = "passport_series", length = 2)
    private String passportSeries;

    @Column(name = "passport_number", length = 7)
    private String passportNumber;

    @Column(name = "suspension_reason")
    private String suspensionReason;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    // LAZY + batch fetching: avoids N+1 across lists of creators without breaking
    // pagination (unlike an @EntityGraph collection fetch, which forces in-memory
    // pagination in Hibernate). Callers must run within a transaction to read this.
    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @CollectionTable(
        name = "creator_profile_options",
        joinColumns = @JoinColumn(name = "creator_id")
    )
    @Column(name = "option_name", length = 30)
    @Enumerated(EnumType.STRING)
    private Set<OrderOption> options = new HashSet<>();
}
