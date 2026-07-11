package uz.tabriko.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import uz.tabriko.domain.entity.Category;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.CreatorTier;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.support.PostgresTestSupport;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Real Postgres (Testcontainers): exercises the actual Flyway-migrated schema, so a
// mismatch between an entity mapping and a migration (e.g. missing column/index) fails
// here instead of surfacing only in production.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CreatorProfileRepositoryIntegrationTest extends PostgresTestSupport {

    @Autowired TestEntityManager em;
    @Autowired CreatorProfileRepository creatorProfileRepo;

    private Category category;

    // V2__seed_data.sql plants 3 verified/complete creators that persist for the whole
    // test class (Flyway migration data isn't rolled back like a test's own transaction).
    // Every assertion in this class checks exact result-set sizes/ordering, so those rows
    // would silently leak into the counts. Wipe them here, inside each test's own
    // transaction, so the rollback after each test restores them for the next one.
    @BeforeEach
    void removeSeedCreators() {
        em.getEntityManager().createNativeQuery(
                "DELETE FROM reviews WHERE order_id = '30000000-0000-0000-0000-000000000001'").executeUpdate();
        em.getEntityManager().createNativeQuery(
                "DELETE FROM deliveries WHERE order_id = '30000000-0000-0000-0000-000000000001'").executeUpdate();
        em.getEntityManager().createNativeQuery(
                "DELETE FROM orders WHERE id = '30000000-0000-0000-0000-000000000001'").executeUpdate();
        em.getEntityManager().createNativeQuery(
                "DELETE FROM portfolio_items WHERE creator_id IN ("
                        + "'10000000-0000-0000-0000-000000000001',"
                        + "'10000000-0000-0000-0000-000000000002',"
                        + "'10000000-0000-0000-0000-000000000003')").executeUpdate();
        em.getEntityManager().createNativeQuery(
                "DELETE FROM creator_profiles WHERE user_id IN ("
                        + "'10000000-0000-0000-0000-000000000001',"
                        + "'10000000-0000-0000-0000-000000000002',"
                        + "'10000000-0000-0000-0000-000000000003')").executeUpdate();
        em.getEntityManager().createNativeQuery(
                "DELETE FROM creator_service WHERE creator_id IN ("
                        + "'10000000-0000-0000-0000-000000000001',"
                        + "'10000000-0000-0000-0000-000000000002',"
                        + "'10000000-0000-0000-0000-000000000003')").executeUpdate();
        em.getEntityManager().createNativeQuery(
                "DELETE FROM users WHERE id IN ("
                        + "'10000000-0000-0000-0000-000000000001',"
                        + "'10000000-0000-0000-0000-000000000002',"
                        + "'10000000-0000-0000-0000-000000000003')").executeUpdate();
    }

    private Category persistCategory(String name) {
        Category cat = new Category();
        cat.setName(name);
        cat.setNameRu(name);
        cat.setNameEn(name);
        return em.persistAndFlush(cat);
    }

    private CreatorProfile persistCreator(String phone, String name, boolean verified, boolean complete,
                                           boolean top, int ratingCount, UserStatus status) {
        User user = new User();
        user.setPhone(phone);
        user.setName(name);
        user.setRole(Role.CREATOR);
        user.setStatus(status);
        em.persist(user);

        CreatorProfile cp = new CreatorProfile();
        cp.setUser(user);
        cp.setCategory(category);
        cp.setPriceFrom(new BigDecimal("50.00"));
        cp.setDeliveryDays(3);
        cp.setVerified(verified);
        cp.setProfileComplete(complete);
        cp.setTop(top);
        cp.setRatingCount(ratingCount);
        cp.setTier(CreatorTier.STANDARD);
        em.persist(cp);
        return cp;
    }

    @Test
    void findAllFiltered_onlyReturnsVerifiedCompleteProfiles() {
        category = persistCategory("Musiqa");
        persistCreator("+998900000001", "Visible Creator", true, true, false, 0, UserStatus.ACTIVE);
        persistCreator("+998900000002", "Unverified Creator", false, true, false, 0, UserStatus.ACTIVE);
        persistCreator("+998900000003", "Incomplete Creator", true, false, false, 0, UserStatus.ACTIVE);
        em.flush();
        em.clear();

        Page<CreatorProfile> page = creatorProfileRepo.findAllFiltered(null, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUser().getName()).isEqualTo("Visible Creator");
    }

    @Test
    void findAllFiltered_filtersByCategoryAndSearch() {
        category = persistCategory("Sport");
        Category other = persistCategory("San'at");

        persistCreator("+998900000004", "Alisher Sport", true, true, false, 0, UserStatus.ACTIVE);
        CreatorProfile otherCategoryCreator = persistCreator("+998900000005", "Alisher Art", true, true, false, 0, UserStatus.ACTIVE);
        otherCategoryCreator.setCategory(other);
        em.flush();
        em.clear();

        Page<CreatorProfile> byCategory = creatorProfileRepo.findAllFiltered(category.getId(), null, null, PageRequest.of(0, 10));
        assertThat(byCategory.getContent()).hasSize(1);
        assertThat(byCategory.getContent().get(0).getUser().getName()).isEqualTo("Alisher Sport");

        Page<CreatorProfile> bySearch = creatorProfileRepo.findAllFiltered(null, "alisher", "%alisher%", PageRequest.of(0, 10));
        assertThat(bySearch.getContent()).hasSize(2);
    }

    @Test
    void findTop10_onlyReturnsTopVerifiedCompleteCreators_orderedByRatingDesc() {
        category = persistCategory("Kino");
        persistCreator("+998900000006", "Top Low Rating", true, true, true, 1, UserStatus.ACTIVE)
                .setAvgRating(new BigDecimal("3.00"));
        persistCreator("+998900000007", "Top High Rating", true, true, true, 5, UserStatus.ACTIVE)
                .setAvgRating(new BigDecimal("4.80"));
        persistCreator("+998900000008", "Not Top", true, true, false, 10, UserStatus.ACTIVE);
        em.flush();
        em.clear();

        var results = creatorProfileRepo.findTop10();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUser().getName()).isEqualTo("Top High Rating");
        assertThat(results.get(1).getUser().getName()).isEqualTo("Top Low Rating");
    }

    @Test
    void findForYou_ordersByRatingCountDescAndRespectsLimit() {
        category = persistCategory("Bolalar");
        persistCreator("+998900000009", "Few Ratings", true, true, false, 2, UserStatus.ACTIVE);
        persistCreator("+998900000010", "Many Ratings", true, true, false, 50, UserStatus.ACTIVE);
        persistCreator("+998900000011", "Medium Ratings", true, true, false, 20, UserStatus.ACTIVE);
        em.flush();
        em.clear();

        var results = creatorProfileRepo.findForYou(PageRequest.of(0, 2));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUser().getName()).isEqualTo("Many Ratings");
        assertThat(results.get(1).getUser().getName()).isEqualTo("Medium Ratings");
    }

    @Test
    void countActiveCreators_onlyCountsVerifiedCompleteAndActiveStatus() {
        category = persistCategory("Biznes");
        persistCreator("+998900000012", "Active Creator", true, true, false, 0, UserStatus.ACTIVE);
        persistCreator("+998900000013", "Blocked Creator", true, true, false, 0, UserStatus.BLOCKED);
        em.flush();
        em.clear();

        long count = creatorProfileRepo.countActiveCreators(UserStatus.ACTIVE);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void findAllWithUser_fetchesUserAndCategoryEagerlyWithinPagination() {
        category = persistCategory("Ta'lim");
        persistCreator("+998900000014", "Any Creator", false, false, false, 0, UserStatus.ACTIVE);
        em.flush();
        em.clear();

        Page<CreatorProfile> page = creatorProfileRepo.findAllWithUser(PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUser().getName()).isEqualTo("Any Creator");
        assertThat(page.getContent().get(0).getCategory().getName()).isEqualTo("Ta'lim");
    }
}
