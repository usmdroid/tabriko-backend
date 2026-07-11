package uz.tabriko.service;

import org.junit.jupiter.api.Test;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.CreatorServiceOffering;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.CreatorTier;
import uz.tabriko.domain.enums.DiscountType;
import uz.tabriko.domain.enums.OrderType;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.dto.response.CreatorResponse;

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
}
