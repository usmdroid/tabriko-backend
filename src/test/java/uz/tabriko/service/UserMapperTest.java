package uz.tabriko.service;

import org.junit.jupiter.api.Test;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.CreatorServiceOffering;
import uz.tabriko.domain.entity.Order;
import uz.tabriko.domain.entity.PortfolioItem;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.CreatorTier;
import uz.tabriko.domain.enums.DiscountType;
import uz.tabriko.domain.enums.OrderOption;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.domain.enums.OrderType;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.dto.response.CreatorResponse;
import uz.tabriko.dto.response.CreatorSelfProfileResponse;
import uz.tabriko.dto.response.OrderResponse;
import uz.tabriko.infrastructure.media.MediaStorageService;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    // --- publicUrl wiring ---

    private UserMapper mapperWithMockStorage(MediaStorageService storage) throws Exception {
        UserMapper m = new UserMapper();
        Field field = UserMapper.class.getDeclaredField("mediaStorage");
        field.setAccessible(true);
        field.set(m, storage);
        return m;
    }

    @Test
    void publicUrl_appliedToCreatorResponseAvatarBannerAndPortfolio() throws Exception {
        MediaStorageService mockStorage = mock(MediaStorageService.class);
        when(mockStorage.publicUrl(any())).thenAnswer(inv -> {
            String raw = inv.getArgument(0);
            return raw == null ? null : "https://cdn.example.com/" + raw.replaceFirst(".*[/\\\\]", "");
        });

        CreatorProfile cp = minimalProfile();
        cp.setAvatarUrl("s3://bucket/avatars/uuid.jpg");
        cp.setBannerUrl("s3://bucket/banners/uuid.jpg");

        PortfolioItem item = new PortfolioItem();
        item.setMediaUrl("s3://bucket/portfolio/uuid.mp4");

        CreatorResponse r = mapperWithMockStorage(mockStorage).toCreatorResponse(cp, List.of(item));

        assertThat(r.getAvatarUrl()).startsWith("https://");
        assertThat(r.getBannerUrl()).startsWith("https://");
        assertThat(r.getPortfolio().get(0).getMediaUrl()).startsWith("https://");
    }

    @Test
    void publicUrl_appliedToSelfProfileResponse() throws Exception {
        MediaStorageService mockStorage = mock(MediaStorageService.class);
        when(mockStorage.publicUrl(any())).thenAnswer(inv -> {
            String raw = inv.getArgument(0);
            return raw == null ? null : "https://cdn.example.com/" + raw.replaceFirst(".*[/\\\\]", "");
        });

        CreatorProfile cp = minimalProfile();
        cp.setAvatarUrl("s3://bucket/avatars/uuid.jpg");
        cp.setBannerUrl("s3://bucket/banners/uuid.jpg");

        CreatorSelfProfileResponse r = mapperWithMockStorage(mockStorage).toCreatorSelfProfileResponse(cp, List.of());

        assertThat(r.getAvatarUrl()).startsWith("https://");
        assertThat(r.getBannerUrl()).startsWith("https://");
    }

    @Test
    void publicUrl_nullInput_returnsNull() throws Exception {
        MediaStorageService mockStorage = mock(MediaStorageService.class);
        when(mockStorage.publicUrl(null)).thenReturn(null);

        CreatorProfile cp = minimalProfile();
        cp.setAvatarUrl(null);
        cp.setBannerUrl(null);

        CreatorResponse r = mapperWithMockStorage(mockStorage).toCreatorResponse(cp, List.of());

        assertThat(r.getAvatarUrl()).isNull();
        assertThat(r.getBannerUrl()).isNull();
    }

    // --- Phone stripping ---

    private Order minimalOrder() {
        User client = new User();
        client.setId(UUID.randomUUID());
        client.setPhone("+998901111111");
        client.setName("Client");
        client.setStatus(UserStatus.ACTIVE);

        User creator = new User();
        creator.setId(UUID.randomUUID());
        creator.setPhone("+998902222222");
        creator.setName("Creator");
        creator.setStatus(UserStatus.ACTIVE);

        Order o = new Order();
        o.setClient(client);
        o.setCreator(creator);
        o.setType(OrderType.VIDEO);
        o.setOption(OrderOption.SURPRISE);
        o.setStatus(OrderStatus.PENDING);
        o.setPrice(new BigDecimal("100.00"));
        return o;
    }

    @Test
    void toCreatorResponse_doesNotExposePhone() {
        CreatorProfile cp = minimalProfile();
        CreatorResponse r = mapper.toCreatorResponse(cp, List.of());
        assertThat(r.getPhone()).isNull();
    }

    @Test
    void toCreatorResponseAdmin_exposesPhone() {
        CreatorProfile cp = minimalProfile();
        CreatorResponse r = mapper.toCreatorResponseAdmin(cp, List.of());
        assertThat(r.getPhone()).isEqualTo("+998901234567");
    }

    @Test
    void toOrderResponse_doesNotExposePhones() {
        Order o = minimalOrder();
        OrderResponse r = mapper.toOrderResponse(o, null, null);
        assertThat(r.getClientPhone()).isNull();
        assertThat(r.getCreatorPhone()).isNull();
    }

    @Test
    void toOrderResponseAdmin_exposesPhones() {
        Order o = minimalOrder();
        OrderResponse r = mapper.toOrderResponseAdmin(o, null, null);
        assertThat(r.getClientPhone()).isEqualTo("+998901111111");
        assertThat(r.getCreatorPhone()).isEqualTo("+998902222222");
    }
}
