package uz.tabriko.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.util.PhoneHashUtil;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.dto.response.BirthdayMatchResponse;
import uz.tabriko.dto.response.UserResponse;
import uz.tabriko.repository.CreatorProfileRepository;
import uz.tabriko.repository.UserRepository;
import uz.tabriko.support.PostgresTestSupport;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "app.jwt.access-secret=test-access-secret-key-must-be-at-least-32-chars!!",
        "app.jwt.refresh-secret=test-refresh-secret-key-must-be-at-least-32-chars!",
        "app.payment.callback-secret=test-callback-secret"
})
@DirtiesContext
class ContactsBirthdayIntegrationTest extends PostgresTestSupport {

    @Autowired private UserRepository userRepo;
    @Autowired private CreatorProfileRepository creatorProfileRepo;
    @Autowired private UserService userService;
    @Autowired private ContactsService contactsService;

    private static int counter = 0;

    private User createUser(String phone, LocalDate birthDate, Role role) {
        User u = new User();
        u.setPhone(phone);
        u.setPhoneHash(PhoneHashUtil.hash(phone));
        u.setName("Test " + ++counter);
        u.setRole(role);
        u.setAccountNumber("TBR-TST" + String.format("%04d", counter));
        if (birthDate != null) u.setBirthDate(birthDate);
        return userRepo.save(u);
    }

    /** Case 1: GET /me returns birthDate after registration sets it. */
    @Test
    @Transactional
    void getMe_returnsBirthDate_whenSet() {
        User u = createUser("+998901110001", LocalDate.of(1995, 3, 15), Role.CLIENT);
        UserResponse resp = userService.getMe(u.getId());
        assertThat(resp.getBirthDate()).isEqualTo(LocalDate.of(1995, 3, 15));
    }

    /** Case 2: happy-path match — A has known phone+birthDate, caller B sends hash(A.phone),
     *  gets back A with correct birthDay/birthMonth, no year, no phone. */
    @Test
    @Transactional
    void birthdayMatch_happyPath_returnsCorrectFields() {
        User userA = createUser("+998902220002", LocalDate.of(1990, 7, 20), Role.CLIENT);
        User callerB = createUser("+998902220003", null, Role.CLIENT);

        String hashOfA = PhoneHashUtil.hash(userA.getPhone());
        List<BirthdayMatchResponse> results = contactsService.findBirthdayMatches(callerB.getId(), List.of(hashOfA));

        assertThat(results).hasSize(1);
        BirthdayMatchResponse r = results.get(0);
        assertThat(r.getUserId()).isEqualTo(userA.getId());
        assertThat(r.getName()).isEqualTo(userA.getName());
        assertThat(r.getBirthDay()).isEqualTo(20);
        assertThat(r.getBirthMonth()).isEqualTo(7);
        assertThat(r.isCreator()).isFalse();
        assertThat(r.getPublicCode()).isNull();
        // no birth year and no phone in the response DTO
    }

    /** Case 2b: creator match includes publicCode. */
    @Test
    @Transactional
    void birthdayMatch_creatorUser_returnsPublicCode() {
        User creatorUser = createUser("+998903330004", LocalDate.of(1988, 11, 5), Role.CREATOR);
        CreatorProfile cp = new CreatorProfile();
        cp.setUser(creatorUser);
        cp.setPublicCode("TST-CRTX");
        cp.setDeliveryDays(3);
        creatorProfileRepo.save(cp);

        User caller = createUser("+998903330005", null, Role.CLIENT);

        String hash = PhoneHashUtil.hash(creatorUser.getPhone());
        List<BirthdayMatchResponse> results = contactsService.findBirthdayMatches(caller.getId(), List.of(hash));

        assertThat(results).hasSize(1);
        BirthdayMatchResponse r = results.get(0);
        assertThat(r.isCreator()).isTrue();
        assertThat(r.getPublicCode()).isEqualTo("TST-CRTX");
    }

    /** Case 3: user without birthDate is excluded from results. */
    @Test
    @Transactional
    void birthdayMatch_userWithoutBirthDate_excluded() {
        User noBirthUser = createUser("+998904440006", null, Role.CLIENT);
        User caller = createUser("+998904440007", null, Role.CLIENT);

        String hash = PhoneHashUtil.hash(noBirthUser.getPhone());
        List<BirthdayMatchResponse> results = contactsService.findBirthdayMatches(caller.getId(), List.of(hash));

        assertThat(results).isEmpty();
    }

    /** Case 4: caller's own hash is excluded even if in the input list. */
    @Test
    @Transactional
    void birthdayMatch_callerOwnHash_excluded() {
        User caller = createUser("+998905550008", LocalDate.of(1992, 4, 10), Role.CLIENT);

        String ownHash = PhoneHashUtil.hash(caller.getPhone());
        List<BirthdayMatchResponse> results = contactsService.findBirthdayMatches(caller.getId(), List.of(ownHash));

        assertThat(results).isEmpty();
    }
}
