package uz.tabriko.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import uz.tabriko.domain.entity.Order;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.WalletTransaction;
import uz.tabriko.domain.enums.OrderOption;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.domain.enums.OrderType;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.TransactionStatus;
import uz.tabriko.domain.enums.TransactionType;
import uz.tabriko.support.PostgresTestSupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Runs the custom repository queries fixed for the N+1/hardening work against a real
// Postgres (Testcontainers), not H2 — this also implicitly exercises the full Flyway
// migration history via Replace.NONE, since @DataJpaTest boots the real datasource
// autoconfiguration (including Flyway) instead of swapping in an embedded database.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryIntegrationTest extends PostgresTestSupport {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CreatorProfileRepository creatorProfileRepo;

    @Autowired
    private WalletTransactionRepository walletTxRepo;

    @Autowired
    private ReviewRepository reviewRepo;

    private User persistUser(Role role, String phoneSuffix) {
        User u = new User();
        u.setPhone("+99890" + phoneSuffix);
        u.setName("Test " + role + " " + phoneSuffix);
        u.setRole(role);
        return em.persistAndFlush(u);
    }

    private CreatorProfile persistCreatorProfile(User creator) {
        CreatorProfile cp = new CreatorProfile();
        cp.setUser(creator);
        cp.setPriceFrom(new BigDecimal("50.00"));
        cp.setDeliveryDays(3);
        cp.setVerified(true);
        cp.setProfileComplete(true);
        return em.persistAndFlush(cp);
    }

    private Order persistOrder(User client, User creator, OrderStatus status) {
        Order o = new Order();
        o.setClient(client);
        o.setCreator(creator);
        o.setType(OrderType.VIDEO);
        o.setOption(OrderOption.SHER);
        o.setRecipientName("Recipient");
        o.setPrice(new BigDecimal("100.00"));
        o.setStatus(status);
        o.setDeadline(Instant.now().plus(3, ChronoUnit.DAYS));
        return em.persistAndFlush(o);
    }

    private WalletTransaction persistTx(User user, BigDecimal amount, TransactionType type, TransactionStatus status, Order order) {
        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setAmount(amount);
        tx.setType(type);
        tx.setStatus(status);
        tx.setOrder(order);
        return em.persistAndFlush(tx);
    }

    @Test
    void walletTransactionRepository_sumByUserAndStatusAndTypes_sumsOnlyMatchingRows() {
        User creator = persistUser(Role.CREATOR, "1000001");
        Order order = persistOrder(persistUser(Role.CLIENT, "1000002"), creator, OrderStatus.ACCEPTED);

        persistTx(creator, new BigDecimal("85.00"), TransactionType.RELEASE, TransactionStatus.COMPLETED, order);
        persistTx(creator, new BigDecimal("15.00"), TransactionType.COMMISSION, TransactionStatus.COMPLETED, order);
        // PENDING row must not be counted as a credit
        persistTx(creator, new BigDecimal("999.00"), TransactionType.RELEASE, TransactionStatus.PENDING, order);

        BigDecimal credits = walletTxRepo.sumByUserAndStatusAndTypes(
                creator.getId(), TransactionStatus.COMPLETED, List.of(TransactionType.RELEASE));
        BigDecimal debits = walletTxRepo.sumByUserAndStatusAndTypes(
                creator.getId(), TransactionStatus.COMPLETED, List.of(TransactionType.COMMISSION));

        assertThat(credits).isEqualByComparingTo("85.00");
        assertThat(debits).isEqualByComparingTo("15.00");
    }

    @Test
    void walletTransactionRepository_computeActiveHold_onlySumsHoldsLinkedToActiveOrders() {
        User client = persistUser(Role.CLIENT, "1000003");
        User creator = persistUser(Role.CREATOR, "1000004");

        Order activeOrder = persistOrder(client, creator, OrderStatus.IN_PROGRESS);
        Order finishedOrder = persistOrder(client, creator, OrderStatus.ACCEPTED);

        persistTx(client, new BigDecimal("100.00"), TransactionType.HOLD, TransactionStatus.COMPLETED, activeOrder);
        persistTx(client, new BigDecimal("200.00"), TransactionType.HOLD, TransactionStatus.COMPLETED, finishedOrder);

        BigDecimal activeHold = walletTxRepo.computeActiveHold(
                client.getId(), TransactionType.HOLD,
                List.of(OrderStatus.PENDING, OrderStatus.IN_PROGRESS, OrderStatus.DELIVERED));

        // Only the IN_PROGRESS order's hold counts; the ACCEPTED order already settled.
        assertThat(activeHold).isEqualByComparingTo("100.00");
    }

    @Test
    void creatorProfileRepository_findAllWithUser_fetchJoinsUserWithoutLazyInitException() {
        User creator = persistUser(Role.CREATOR, "1000005");
        persistCreatorProfile(creator);
        em.clear();

        var page = creatorProfileRepo.findAllWithUser(PageRequest.of(0, 50));

        var found = page.getContent().stream()
                .filter(cp -> cp.getUserId().equals(creator.getId()))
                .findFirst()
                .orElseThrow();
        // Accessing the lazy association outside the original persistence context would
        // throw LazyInitializationException if the JOIN FETCH weren't actually applied.
        assertThat(found.getUser().getName()).isEqualTo(creator.getName());
    }

    @Test
    void reviewRepository_calculateAvgRating_returnsNullWhenNoReviews() {
        User creator = persistUser(Role.CREATOR, "1000006");

        Double avg = reviewRepo.calculateAvgRating(creator.getId());

        assertThat(avg).isNull();
    }

    @Test
    void userRepository_findByIdForUpdate_locksAndReturnsRow() {
        User user = persistUser(Role.CLIENT, "1000007");

        var found = userRepo.findByIdForUpdate(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getPhone()).isEqualTo(user.getPhone());
    }
}
