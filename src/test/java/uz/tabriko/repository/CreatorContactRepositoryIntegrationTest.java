package uz.tabriko.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import uz.tabriko.domain.entity.CreatorContact;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.support.PostgresTestSupport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CreatorContactRepositoryIntegrationTest extends PostgresTestSupport {

    @Autowired TestEntityManager em;
    @Autowired CreatorContactRepository contactRepo;

    private User persistCreator(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setName("Creator " + phone);
        user.setRole(Role.CREATOR);
        user.setStatus(UserStatus.ACTIVE);
        return em.persistAndFlush(user);
    }

    private CreatorContact contact(UUID creatorId, String phone, String label) {
        CreatorContact c = new CreatorContact();
        c.setCreatorId(creatorId);
        c.setPhone(phone);
        c.setLabel(label);
        return c;
    }

    @Test
    void existsByCreatorIdAndPhone_returnsTrueForExistingContact() {
        User creator = persistCreator("+998900001001");
        em.persistAndFlush(contact(creator.getId(), "+998901111111", "Manager"));
        em.clear();

        assertThat(contactRepo.existsByCreatorIdAndPhone(creator.getId(), "+998901111111")).isTrue();
    }

    @Test
    void existsByCreatorIdAndPhone_returnsFalseWhenContactBelongsToDifferentCreator() {
        User creatorA = persistCreator("+998900001002");
        User creatorB = persistCreator("+998900001003");
        em.persistAndFlush(contact(creatorA.getId(), "+998902222222", null));
        em.clear();

        assertThat(contactRepo.existsByCreatorIdAndPhone(creatorB.getId(), "+998902222222")).isFalse();
    }

    @Test
    void uniqueConstraint_rejectsDuplicateCreatorPhone() {
        User creator = persistCreator("+998900001004");
        em.persistAndFlush(contact(creator.getId(), "+998903333333", null));

        assertThatThrownBy(() -> contactRepo.saveAndFlush(contact(creator.getId(), "+998903333333", "dupe")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueConstraint_allowsSamePhoneForDifferentCreators() {
        User creatorA = persistCreator("+998900001005");
        User creatorB = persistCreator("+998900001006");
        em.persistAndFlush(contact(creatorA.getId(), "+998904444444", null));

        // Should not throw — cross-creator uniqueness is NOT enforced
        CreatorContact saved = contactRepo.saveAndFlush(contact(creatorB.getId(), "+998904444444", "shared manager"));
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void findByIdAndCreatorId_returnsPresentForCorrectCreator() {
        User creator = persistCreator("+998900001007");
        CreatorContact c = em.persistAndFlush(contact(creator.getId(), "+998905555555", "Label"));
        em.clear();

        Optional<CreatorContact> result = contactRepo.findByIdAndCreatorId(c.getId(), creator.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getPhone()).isEqualTo("+998905555555");
    }

    @Test
    void findByIdAndCreatorId_returnsEmptyForWrongCreator() {
        User creatorA = persistCreator("+998900001008");
        User creatorB = persistCreator("+998900001009");
        CreatorContact c = em.persistAndFlush(contact(creatorA.getId(), "+998906666666", null));
        em.clear();

        Optional<CreatorContact> result = contactRepo.findByIdAndCreatorId(c.getId(), creatorB.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByCreatorIdOrderByCreatedAtAsc_returnsScopedAndOrdered() {
        User creator = persistCreator("+998900001010");
        CreatorContact c1 = em.persistAndFlush(contact(creator.getId(), "+998907777771", "first"));
        CreatorContact c2 = em.persistAndFlush(contact(creator.getId(), "+998907777772", "second"));
        em.clear();

        List<CreatorContact> results = contactRepo.findByCreatorIdOrderByCreatedAtAsc(creator.getId());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo(c1.getId());
        assertThat(results.get(1).getId()).isEqualTo(c2.getId());
    }

    @Test
    void findByCreatorIdIn_batchFetchesOnlyRequestedCreators() {
        User creatorA = persistCreator("+998900001011");
        User creatorB = persistCreator("+998900001012");
        User creatorC = persistCreator("+998900001013");
        em.persistAndFlush(contact(creatorA.getId(), "+998908880001", null));
        em.persistAndFlush(contact(creatorB.getId(), "+998908880002", null));
        em.persistAndFlush(contact(creatorC.getId(), "+998908880003", null));
        em.clear();

        List<CreatorContact> results = contactRepo.findByCreatorIdIn(List.of(creatorA.getId(), creatorB.getId()));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(CreatorContact::getCreatorId)
                .containsExactlyInAnyOrder(creatorA.getId(), creatorB.getId());
    }
}
