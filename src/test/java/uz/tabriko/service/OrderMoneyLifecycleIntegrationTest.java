package uz.tabriko.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.common.util.HmacUtil;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.WalletTransaction;
import uz.tabriko.domain.enums.OrderOption;
import uz.tabriko.domain.enums.OrderType;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.TransactionStatus;
import uz.tabriko.domain.enums.TransactionType;
import uz.tabriko.dto.request.CreateOrderRequest;
import uz.tabriko.dto.request.DeliverOrderRequest;
import uz.tabriko.dto.request.RejectOrderRequest;
import uz.tabriko.dto.request.TopUpRequest;
import uz.tabriko.dto.request.WalletCallbackRequest;
import uz.tabriko.dto.request.WithdrawRequest;
import uz.tabriko.dto.response.OrderResponse;
import uz.tabriko.dto.response.TopUpInitResponse;
import uz.tabriko.infrastructure.payment.PaymentProviderType;
import uz.tabriko.repository.CreatorProfileRepository;
import uz.tabriko.repository.UserRepository;
import uz.tabriko.repository.WalletTransactionRepository;
import uz.tabriko.support.PostgresTestSupport;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// End-to-end money-lifecycle checks against a real Postgres (Testcontainers), exercising the
// actual repository aggregate queries fixed for N+1 rather than mocking them out, as
// WalletServiceTest already does at the unit level.
@SpringBootTest
@TestPropertySource(properties = {
        "app.jwt.access-secret=test-access-secret-key-must-be-at-least-32-chars!!",
        "app.jwt.refresh-secret=test-refresh-secret-key-must-be-at-least-32-chars!",
        "app.payment.callback-secret=test-callback-secret"
})
@DirtiesContext
class OrderMoneyLifecycleIntegrationTest extends PostgresTestSupport {

    @Autowired private OrderService orderService;
    @Autowired private WalletService walletService;
    @Autowired private UserRepository userRepo;
    @Autowired private CreatorProfileRepository creatorProfileRepo;
    @Autowired private WalletTransactionRepository walletTxRepo;

    private static int counter = 0;

    private User createUser(Role role) {
        User u = new User();
        u.setPhone("+9989" + String.format("%07d", ++counter));
        u.setName("Test " + role + " " + counter);
        u.setRole(role);
        return userRepo.save(u);
    }

    private User createCreatorWithProfile(BigDecimal priceFrom) {
        User creator = createUser(Role.CREATOR);
        CreatorProfile cp = new CreatorProfile();
        cp.setUser(creator);
        cp.setPriceFrom(priceFrom);
        cp.setDeliveryDays(3);
        cp.setVerified(true);
        cp.setProfileComplete(true);
        creatorProfileRepo.save(cp);
        return creator;
    }

    private void topUpCompleted(User user, BigDecimal amount) {
        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setAmount(amount);
        tx.setType(TransactionType.TOPUP);
        tx.setStatus(TransactionStatus.COMPLETED);
        walletTxRepo.save(tx);
    }

    private OrderResponse createOrder(User client, User creator) {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCreatorId(creator.getId());
        req.setType(OrderType.VIDEO);
        req.setOption(OrderOption.SHER);
        req.setRecipientName("Recipient");
        return orderService.createOrder(client.getId(), req);
    }

    @Test
    @Transactional
    void acceptOrder_releasesNetAmountToCreatorAndRestoresClientAvailableBalance() {
        User client = createUser(Role.CLIENT);
        User creator = createCreatorWithProfile(new BigDecimal("100.00"));
        topUpCompleted(client, new BigDecimal("500.00"));

        OrderResponse order = createOrder(client, creator);

        // HOLD debits client's balance immediately and is also reflected as an active hold
        // while the order is open, per WalletService's ledger design.
        assertThat(walletService.getBalance(client.getId())).isEqualByComparingTo("400.00");
        assertThat(walletService.getAvailableBalance(client.getId())).isEqualByComparingTo("300.00");
        assertThat(walletService.getBalance(creator.getId())).isEqualByComparingTo("0.00");

        DeliverOrderRequest deliverReq = new DeliverOrderRequest();
        deliverReq.setMediaUrl("http://example.test/media/clean.mp4");
        orderService.deliverOrder(creator.getId(), order.getId(), deliverReq);

        orderService.acceptOrder(client.getId(), order.getId());

        // Order settled: the hold is released back into the client's available balance,
        // and the creator is credited price minus the 15% commission (100 - 15 = 85).
        assertThat(walletService.getBalance(client.getId())).isEqualByComparingTo("400.00");
        assertThat(walletService.getAvailableBalance(client.getId())).isEqualByComparingTo("400.00");
        assertThat(walletService.getBalance(creator.getId())).isEqualByComparingTo("85.00");
    }

    @Test
    @Transactional
    void rejectOrder_refundsClientInFull() {
        User client = createUser(Role.CLIENT);
        User creator = createCreatorWithProfile(new BigDecimal("100.00"));
        topUpCompleted(client, new BigDecimal("500.00"));

        OrderResponse order = createOrder(client, creator);
        assertThat(walletService.getAvailableBalance(client.getId())).isEqualByComparingTo("300.00");

        DeliverOrderRequest deliverReq = new DeliverOrderRequest();
        deliverReq.setMediaUrl("http://example.test/media/clean.mp4");
        orderService.deliverOrder(creator.getId(), order.getId(), deliverReq);

        RejectOrderRequest rejectReq = new RejectOrderRequest();
        rejectReq.setReason("Not what I asked for");
        orderService.rejectOrder(client.getId(), order.getId(), rejectReq);

        // REFUND credit restores the client's balance to what it was before the hold.
        assertThat(walletService.getBalance(client.getId())).isEqualByComparingTo("500.00");
        assertThat(walletService.getAvailableBalance(client.getId())).isEqualByComparingTo("500.00");
        assertThat(walletService.getBalance(creator.getId())).isEqualByComparingTo("0.00");
    }

    @Test
    @Transactional
    void withdraw_reservesFundsImmediatelyViaAvailableBalance_withoutTouchingCompletedBalance() {
        User user = createUser(Role.CLIENT);
        topUpCompleted(user, new BigDecimal("500.00"));

        WithdrawRequest req = new WithdrawRequest();
        req.setAmount(new BigDecimal("200.00"));
        walletService.withdraw(user.getId(), req);

        // The WITHDRAW tx is PENDING, so it does not (yet) debit getBalance(), but it
        // immediately reserves funds via getAvailableBalance() so a second withdrawal
        // can't double-spend the same money before the first is settled.
        assertThat(walletService.getBalance(user.getId())).isEqualByComparingTo("500.00");
        assertThat(walletService.getAvailableBalance(user.getId())).isEqualByComparingTo("300.00");

        WithdrawRequest second = new WithdrawRequest();
        second.setAmount(new BigDecimal("350.00"));
        assertThatThrownBy(() -> walletService.withdraw(user.getId(), second))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    @Transactional
    void handleCallback_duplicateCallback_doesNotDoubleCreditBalance() {
        User user = createUser(Role.CLIENT);

        TopUpRequest topUpReq = new TopUpRequest();
        topUpReq.setAmount(new BigDecimal("150.00"));
        topUpReq.setProvider(PaymentProviderType.CLICK);
        TopUpInitResponse init = walletService.topUp(user.getId(), topUpReq);

        assertThat(walletService.getBalance(user.getId())).isEqualByComparingTo("0.00");

        WalletCallbackRequest callback = signedCallback(init.getWalletTransactionId(), new BigDecimal("150.00"), "provider-ref-1");

        walletService.handleCallback(callback);
        assertThat(walletService.getBalance(user.getId())).isEqualByComparingTo("150.00");

        // Provider retries/duplicates the same callback; must be idempotent.
        walletService.handleCallback(callback);
        assertThat(walletService.getBalance(user.getId())).isEqualByComparingTo("150.00");
    }

    private WalletCallbackRequest signedCallback(Long transactionId, BigDecimal amount, String providerRef) {
        WalletCallbackRequest req = new WalletCallbackRequest();
        req.setTransactionId(transactionId);
        req.setAmount(amount);
        req.setProviderRef(providerRef);
        String payload = transactionId + ":" + amount.toPlainString() + ":" + providerRef;
        req.setSignature(HmacUtil.sha256Hex(payload, "test-callback-secret"));
        return req;
    }
}
