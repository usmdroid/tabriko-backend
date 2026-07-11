package uz.tabriko.service;

import org.junit.jupiter.api.Test;
import uz.tabriko.domain.entity.CreatorServiceOffering;
import uz.tabriko.domain.enums.DiscountType;
import uz.tabriko.domain.enums.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ServicePricingCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-01-15T00:00:00Z");

    private CreatorServiceOffering offering(BigDecimal price, DiscountType type, BigDecimal discountValue,
                                             Instant startsAt, Instant endsAt) {
        CreatorServiceOffering svc = new CreatorServiceOffering();
        svc.setType(OrderType.VIDEO);
        svc.setPrice(price);
        svc.setDiscountType(type);
        svc.setDiscountValue(discountValue);
        svc.setDiscountStartsAt(startsAt);
        svc.setDiscountEndsAt(endsAt);
        return svc;
    }

    // ===== NONE =====

    @Test
    void effectivePrice_none_returnsOriginalPrice() {
        CreatorServiceOffering svc = offering(new BigDecimal("100.00"), DiscountType.NONE, null, null, null);

        assertThat(ServicePricingCalculator.effectivePrice(svc, NOW)).isEqualByComparingTo("100.00");
        assertThat(ServicePricingCalculator.isOnSale(svc, NOW)).isFalse();
        assertThat(ServicePricingCalculator.discountPercent(svc, NOW)).isNull();
    }

    // ===== PRICE =====

    @Test
    void effectivePrice_price_returnsDiscountValueWhenActive() {
        CreatorServiceOffering svc = offering(new BigDecimal("100.00"), DiscountType.PRICE,
                new BigDecimal("70.00"), null, null);

        assertThat(ServicePricingCalculator.effectivePrice(svc, NOW)).isEqualByComparingTo("70.00");
        assertThat(ServicePricingCalculator.isOnSale(svc, NOW)).isTrue();
        assertThat(ServicePricingCalculator.discountPercent(svc, NOW)).isEqualTo(30);
    }

    // ===== PERCENT =====

    @Test
    void effectivePrice_percent_appliesPercentageOff() {
        CreatorServiceOffering svc = offering(new BigDecimal("100.00"), DiscountType.PERCENT,
                new BigDecimal("25"), null, null);

        assertThat(ServicePricingCalculator.effectivePrice(svc, NOW)).isEqualByComparingTo("75.00");
        assertThat(ServicePricingCalculator.isOnSale(svc, NOW)).isTrue();
        assertThat(ServicePricingCalculator.discountPercent(svc, NOW)).isEqualTo(25);
    }

    @Test
    void effectivePrice_percent_roundsHalfUp() {
        // 33% off 100 => 67.00 exactly, but exercise a case with rounding: 10% off 99.99
        CreatorServiceOffering svc = offering(new BigDecimal("99.99"), DiscountType.PERCENT,
                new BigDecimal("10"), null, null);

        // 99.99 * 90 / 100 = 89.991 -> HALF_UP -> 89.99
        assertThat(ServicePricingCalculator.effectivePrice(svc, NOW)).isEqualByComparingTo("89.99");
    }

    // ===== date-window boundaries =====

    @Test
    void discount_nullEndsAt_meansInfiniteWindow() {
        CreatorServiceOffering svc = offering(new BigDecimal("100.00"), DiscountType.PERCENT,
                new BigDecimal("10"), NOW.minus(1, ChronoUnit.DAYS), null);

        Instant farFuture = NOW.plus(3650, ChronoUnit.DAYS);
        assertThat(ServicePricingCalculator.isOnSale(svc, farFuture)).isTrue();
        assertThat(ServicePricingCalculator.effectivePrice(svc, farFuture)).isEqualByComparingTo("90.00");
    }

    @Test
    void discount_beforeStartsAt_isNotActive() {
        CreatorServiceOffering svc = offering(new BigDecimal("100.00"), DiscountType.PERCENT,
                new BigDecimal("10"), NOW.plus(1, ChronoUnit.DAYS), NOW.plus(2, ChronoUnit.DAYS));

        assertThat(ServicePricingCalculator.isOnSale(svc, NOW)).isFalse();
        assertThat(ServicePricingCalculator.effectivePrice(svc, NOW)).isEqualByComparingTo("100.00");
        assertThat(ServicePricingCalculator.discountPercent(svc, NOW)).isNull();
    }

    @Test
    void discount_atExactStartsAt_isActive() {
        Instant startsAt = NOW;
        CreatorServiceOffering svc = offering(new BigDecimal("100.00"), DiscountType.PERCENT,
                new BigDecimal("10"), startsAt, NOW.plus(1, ChronoUnit.DAYS));

        assertThat(ServicePricingCalculator.isOnSale(svc, startsAt)).isTrue();
    }

    @Test
    void discount_afterEndsAt_isNotActive() {
        CreatorServiceOffering svc = offering(new BigDecimal("100.00"), DiscountType.PERCENT,
                new BigDecimal("10"), NOW.minus(2, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS));

        assertThat(ServicePricingCalculator.isOnSale(svc, NOW)).isFalse();
        assertThat(ServicePricingCalculator.effectivePrice(svc, NOW)).isEqualByComparingTo("100.00");
    }

    @Test
    void discount_atExactEndsAt_isStillActive() {
        Instant endsAt = NOW;
        CreatorServiceOffering svc = offering(new BigDecimal("100.00"), DiscountType.PERCENT,
                new BigDecimal("10"), NOW.minus(1, ChronoUnit.DAYS), endsAt);

        assertThat(ServicePricingCalculator.isOnSale(svc, endsAt)).isTrue();
    }

    @Test
    void discount_noStartsAt_isActiveImmediately() {
        CreatorServiceOffering svc = offering(new BigDecimal("100.00"), DiscountType.PERCENT,
                new BigDecimal("10"), null, NOW.plus(1, ChronoUnit.DAYS));

        assertThat(ServicePricingCalculator.isOnSale(svc, NOW)).isTrue();
    }
}
