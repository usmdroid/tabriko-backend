package uz.tabriko.dto.response;

import lombok.Data;
import uz.tabriko.domain.enums.CreatorTier;
import uz.tabriko.domain.enums.OrderOption;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
public class CreatorResponse {
    private UUID id;
    private String name;
    private String phone;
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
    private CreatorTier tier;
    private Set<OrderOption> options;
    private List<CreatorServiceResponse> services;
    private List<PortfolioItemResponse> portfolio;
    private List<CreatorContactResponse> contacts = new ArrayList<>();
    private List<RequisiteItemResponse> requisites = new ArrayList<>();
    private String publicCode;
    private String status;
    private Instant createdAt;
}
