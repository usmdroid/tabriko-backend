package uz.tabriko.dto.response;

import lombok.Data;
import uz.tabriko.domain.enums.CreatorTier;
import uz.tabriko.domain.enums.OrderOption;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
public class CreatorSelfProfileResponse {
    private UUID id;
    private String name;
    private String avatarUrl;
    private String bannerUrl;
    private String bio;
    private CategoryResponse category;
    private BigDecimal avgRating;
    private int ratingCount;
    private BigDecimal priceFrom;
    private BigDecimal originalPriceFrom;
    private boolean onSale;
    private int deliveryDays;
    private boolean isTop;
    private boolean isExclusive;
    private boolean isVerified;
    private boolean accepting;
    private Set<OrderOption> options;
    private List<CreatorServiceResponse> services;
    private List<PortfolioItemResponse> portfolio;
    private String publicCode;
    private String status;
    private Instant createdAt;
    private CreatorTier tier;

    // KYC (masked — never raw values)
    private boolean idProvided;
    private String idDocumentNumberMasked;
    private String idDocumentUrl;

    // Payout (masked)
    private boolean payoutProvided;
    private String payoutCardMasked;
    private String payoutAccountMasked;
    private String payoutHolder;

    // Social
    private String socialTelegram;
    private String socialInstagram;

    // Completeness gate
    private boolean profileComplete;
    private List<String> missing;
}
