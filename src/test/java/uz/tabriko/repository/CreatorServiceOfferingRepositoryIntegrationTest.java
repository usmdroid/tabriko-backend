package uz.tabriko.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import uz.tabriko.domain.entity.CreatorServiceOffering;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.DiscountType;
import uz.tabriko.domain.enums.OrderType;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.support.PostgresTestSupport;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Real Postgres (Testcontainers): exercises the full Flyway history including V27's
// creator_service table + unique (creator_id, type) constraint, and confirms Hibernate's
// entity mapping matches the migration (ddl-auto=validate runs on context startup).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CreatorServiceOfferingRepositoryIntegrationTest extends PostgresTestSupport {

    @Autowired TestEntityManager em;
    @Autowired CreatorServiceOfferingRepository serviceOfferingRepo;

    private User persistCreator(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setName("Creator " + phone);
        user.setRole(Role.CREATOR);
        user.setStatus(UserStatus.ACTIVE);
        return em.persistAndFlush(user);
    }

    private CreatorServiceOffering offering(User creator, OrderType type, BigDecimal price) {
        CreatorServiceOffering svc = new CreatorServiceOffering();
        svc.setCreator(creator);
        svc.setType(type);
        svc.setPrice(price);
        svc.setDiscountType(DiscountType.NONE);
        return svc;
    }

    @Test
    void uniqueConstraint_rejectsDuplicateCreatorAndType() {
        User creator = persistCreator("+998900000101");
        em.persistAndFlush(offering(creator, OrderType.VIDEO, new BigDecimal("50.00")));

        assertThatThrownBy(() -> serviceOfferingRepo.saveAndFlush(offering(creator, OrderType.VIDEO, new BigDecimal("60.00"))))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByCreator_IdAndType_isScopedPerCreator_noCrossCreatorLeak() {
        User creatorA = persistCreator("+998900000102");
        User creatorB = persistCreator("+998900000103");
        em.persistAndFlush(offering(creatorA, OrderType.VIDEO, new BigDecimal("50.00")));
        em.persistAndFlush(offering(creatorB, OrderType.VIDEO, new BigDecimal("999.00")));
        em.clear();

        Optional<CreatorServiceOffering> found = serviceOfferingRepo.findByCreator_IdAndType(creatorA.getId(), OrderType.VIDEO);

        assertThat(found).isPresent();
        assertThat(found.get().getPrice()).isEqualByComparingTo("50.00");
        assertThat(found.get().getCreator().getId()).isEqualTo(creatorA.getId());
    }

    @Test
    void findByCreator_IdIn_batchFetchesOnlyRequestedCreators() {
        User creatorA = persistCreator("+998900000104");
        User creatorB = persistCreator("+998900000105");
        User creatorC = persistCreator("+998900000106");
        em.persistAndFlush(offering(creatorA, OrderType.VIDEO, new BigDecimal("10.00")));
        em.persistAndFlush(offering(creatorB, OrderType.VIDEO, new BigDecimal("20.00")));
        em.persistAndFlush(offering(creatorC, OrderType.VIDEO, new BigDecimal("30.00")));
        em.clear();

        List<CreatorServiceOffering> results = serviceOfferingRepo.findByCreator_IdIn(List.of(creatorA.getId(), creatorB.getId()));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(s -> s.getCreator().getId())
            .containsExactlyInAnyOrder(creatorA.getId(), creatorB.getId());
    }

    @Test
    void findByCreator_IdAndType_returnsEmptyForUnknownCreator() {
        Optional<CreatorServiceOffering> found = serviceOfferingRepo.findByCreator_IdAndType(UUID.randomUUID(), OrderType.VIDEO);

        assertThat(found).isEmpty();
    }
}
