package uz.tabriko.dto.response;

import lombok.Data;
import uz.tabriko.domain.enums.OrderOption;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
public class CreatorResponse {
    private UUID id;
    private String name;
    private String phone;
    private String avatarUrl;
    private String bio;
    private CategoryResponse category;
    private BigDecimal avgRating;
    private int ratingCount;
    private BigDecimal priceFrom;
    private int deliveryDays;
    private boolean isTop;
    private boolean isExclusive;
    private boolean isVerified;
    private boolean accepting;
    private Set<OrderOption> options;
    private List<PortfolioItemResponse> portfolio;
    private String status;      // user account status (lowercased)
    private Instant createdAt;  // user registration date
}
