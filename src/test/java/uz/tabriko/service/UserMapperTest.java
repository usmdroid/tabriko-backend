package uz.tabriko.service;

import org.junit.jupiter.api.Test;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.CreatorServiceOffering;
import uz.tabriko.domain.entity.PortfolioItem;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.CreatorTier;
import uz.tabriko.domain.enums.DiscountType;
import uz.tabriko.domain.enums.OrderType;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.dto.response.CreatorResponse;
import uz.tabriko.dto.response.CreatorSelfProfileResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    private CreatorProfile minimalProfile() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPhone("+998901234567");
        user.setStatus(UserStatus.ACTIVE);

        CreatorProfile cp = new CreatorProfile();
        cp.setUserId(user.getId());
        cp.setUser(user);
        cp.setAvgRating(BigDecimal.ZERO);
        cp.setTier(CreatorTier.STANDARD);
        cp.setOptions(Set.of());
        return cp;
    }

    private CreatorServiceOffering service(boolean accepting, boolean onSale) {
        CreatorServiceOffering s = new CreatorServiceOffering();
        s.setType(OrderType.VIDEO);
        s.setPrice(new BigDecimal("100.00"));
        s.setAccepting(accepting);
        if (onSale) {
            s.setDiscountType(DiscountType.PERCENT);
            s.setDiscountValue(new BigDecimal("20"));
        } else {
            s.setDiscountType(DiscountType.NONE);
        }
        return s;
    }

    @Test
    void cardOnSale_isFalse_whenOnlyNonAcceptingServiceIsOnSale() {
        CreatorServiceOffering nonAcceptingOnSale = service(false, true);
        CreatorServiceOffering acceptingNotOnSale = service(true, false);

        CreatorResponse r = mapper.toCreatorResponse(
                minimalProfile(), List.of(), List.of(nonAcceptingOnSale, acceptingNotOnSale));

        assertThat(r.isOnSale()).isFalse();
    }

    @Test
    void cardOnSale_isTrue_whenAcceptingServiceIsOnSale() {
        CreatorServiceOffering acceptingOnSale = service(true, true);
        CreatorServiceOffering nonAcceptingOnSale = service(false, true);

        CreatorResponse r = mapper.toCreatorResponse(
                minimalProfile(), List.of(), List.of(acceptingOnSale, nonAcceptingOnSale));

        assertThat(r.isOnSale()).isTrue();
    }

    @Test
    void cardOnSale_isFalse_whenNoServicesAccepting() {
        CreatorServiceOffering nonAccepting = service(false, true);

        CreatorResponse r = mapper.toCreatorResponse(
                minimalProfile(), List.of(), List.of(nonAccepting));

        assertThat(r.isOnSale()).isFalse();
    }

    // --- computeMissingItems ---

    private CreatorProfile completeProfile() {
        CreatorProfile cp = minimalProfile();
        cp.setBio("I make videos");
        cp.setPriceFrom(new BigDecimal("50.00"));
        cp.setDeliveryDays(3);
        cp.setSocialTelegram("@creator");
        cp.setIdDocumentNumber("AA1234567");
        cp.setIdDocumentUrl("https://example.com/id.jpg");
        cp.setPayoutCard("8600123412341234");
        return cp;
    }

    private PortfolioItem portfolioItem() {
        PortfolioItem item = new PortfolioItem();
        item.setMediaUrl("https://example.com/media.jpg");
        return item;
    }

    private List<String> missing(CreatorProfile cp, List<PortfolioItem> portfolio) {
        CreatorSelfProfileResponse r = mapper.toCreatorSelfProfileResponse(cp, portfolio);
        return r.getMissing();
    }

    @Test
    void missing_bio_whenBioIsBlank() {
        CreatorProfile cp = completeProfile();
        cp.setBio("  ");
        assertThat(missing(cp, List.of(portfolioItem()))).contains("bio");
    }

    @Test
    void missing_bio_whenBioIsNull() {
        CreatorProfile cp = completeProfile();
        cp.setBio(null);
        assertThat(missing(cp, List.of(portfolioItem()))).contains("bio");
    }

    @Test
    void notMissing_bio_whenBioSet() {
        CreatorProfile cp = completeProfile();
        assertThat(missing(cp, List.of(portfolioItem()))).doesNotContain("bio");
    }

    @Test
    void missing_priceFrom_whenZero() {
        CreatorProfile cp = completeProfile();
        cp.setPriceFrom(BigDecimal.ZERO);
        assertThat(missing(cp, List.of(portfolioItem()))).contains("priceFrom");
    }

    @Test
    void missing_priceFrom_whenNull() {
        CreatorProfile cp = completeProfile();
        cp.setPriceFrom(null);
        assertThat(missing(cp, List.of(portfolioItem()))).contains("priceFrom");
    }

    @Test
    void notMissing_priceFrom_whenPositive() {
        CreatorProfile cp = completeProfile();
        assertThat(missing(cp, List.of(portfolioItem()))).doesNotContain("priceFrom");
    }

    @Test
    void missing_deliveryDays_whenZero() {
        CreatorProfile cp = completeProfile();
        cp.setDeliveryDays(0);
        assertThat(missing(cp, List.of(portfolioItem()))).contains("deliveryDays");
    }

    @Test
    void notMissing_deliveryDays_whenPositive() {
        CreatorProfile cp = completeProfile();
        assertThat(missing(cp, List.of(portfolioItem()))).doesNotContain("deliveryDays");
    }

    @Test
    void missing_social_whenBothBlank() {
        CreatorProfile cp = completeProfile();
        cp.setSocialTelegram(null);
        cp.setSocialInstagram(null);
        assertThat(missing(cp, List.of(portfolioItem()))).contains("social");
    }

    @Test
    void notMissing_social_whenTelegramSet() {
        CreatorProfile cp = completeProfile();
        cp.setSocialInstagram(null);
        assertThat(missing(cp, List.of(portfolioItem()))).doesNotContain("social");
    }

    @Test
    void notMissing_social_whenInstagramSet() {
        CreatorProfile cp = completeProfile();
        cp.setSocialTelegram(null);
        cp.setSocialInstagram("@creator_ig");
        assertThat(missing(cp, List.of(portfolioItem()))).doesNotContain("social");
    }

    @Test
    void missing_portfolio_whenEmpty() {
        CreatorProfile cp = completeProfile();
        assertThat(missing(cp, List.of())).contains("portfolio");
    }

    @Test
    void notMissing_portfolio_whenHasItem() {
        CreatorProfile cp = completeProfile();
        assertThat(missing(cp, List.of(portfolioItem()))).doesNotContain("portfolio");
    }

    @Test
    void missing_passport_whenDocNumberMissing() {
        CreatorProfile cp = completeProfile();
        cp.setIdDocumentNumber(null);
        assertThat(missing(cp, List.of(portfolioItem()))).contains("passport");
    }

    @Test
    void missing_passport_whenDocUrlMissing() {
        CreatorProfile cp = completeProfile();
        cp.setIdDocumentUrl(null);
        assertThat(missing(cp, List.of(portfolioItem()))).contains("passport");
    }

    @Test
    void notMissing_passport_whenBothSet() {
        CreatorProfile cp = completeProfile();
        assertThat(missing(cp, List.of(portfolioItem()))).doesNotContain("passport");
    }

    @Test
    void missing_payment_whenNoPayoutMethod() {
        CreatorProfile cp = completeProfile();
        cp.setPayoutCard(null);
        cp.setPayoutAccount(null);
        assertThat(missing(cp, List.of(portfolioItem()))).contains("payment");
    }

    @Test
    void notMissing_payment_whenCardSet() {
        CreatorProfile cp = completeProfile();
        assertThat(missing(cp, List.of(portfolioItem()))).doesNotContain("payment");
    }

    @Test
    void notMissing_payment_whenAccountSet() {
        CreatorProfile cp = completeProfile();
        cp.setPayoutCard(null);
        cp.setPayoutAccount("20210000123456789");
        assertThat(missing(cp, List.of(portfolioItem()))).doesNotContain("payment");
    }

    @Test
    void profileComplete_whenAllFieldsSet() {
        CreatorProfile cp = completeProfile();
        CreatorSelfProfileResponse r = mapper.toCreatorSelfProfileResponse(cp, List.of(portfolioItem()));
        assertThat(r.getMissing()).isEmpty();
        assertThat(r.isProfileComplete()).isTrue();
    }
}
