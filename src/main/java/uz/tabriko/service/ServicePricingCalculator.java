package uz.tabriko.service;

import uz.tabriko.domain.entity.CreatorServiceOffering;
import uz.tabriko.domain.enums.DiscountType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

// Pure domain calculation for per-service effective pricing — no repository/service
// dependencies, so it can be unit tested directly against fixed instants.
public final class ServicePricingCalculator {

    private ServicePricingCalculator() {
    }

    public static BigDecimal effectivePrice(CreatorServiceOffering svc, Instant now) {
        if (!isDiscountActive(svc, now)) {
            return svc.getPrice();
        }
        return switch (svc.getDiscountType()) {
            case PRICE -> svc.getDiscountValue();
            case PERCENT -> svc.getPrice()
                    .multiply(BigDecimal.valueOf(100).subtract(svc.getDiscountValue()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case NONE -> svc.getPrice();
        };
    }

    public static boolean isOnSale(CreatorServiceOffering svc, Instant now) {
        return isDiscountActive(svc, now) && effectivePrice(svc, now).compareTo(svc.getPrice()) < 0;
    }

    public static Integer discountPercent(CreatorServiceOffering svc, Instant now) {
        if (!isOnSale(svc, now)) {
            return null;
        }
        BigDecimal diff = svc.getPrice().subtract(effectivePrice(svc, now));
        return diff.multiply(BigDecimal.valueOf(100))
                .divide(svc.getPrice(), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private static boolean isDiscountActive(CreatorServiceOffering svc, Instant now) {
        if (svc.getDiscountType() == DiscountType.NONE) {
            return false;
        }
        Instant starts = svc.getDiscountStartsAt();
        Instant ends = svc.getDiscountEndsAt();
        if (starts != null && now.isBefore(starts)) {
            return false;
        }
        return ends == null || !now.isAfter(ends);
    }
}
