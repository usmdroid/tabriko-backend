package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.*;
import uz.tabriko.domain.enums.*;
import uz.tabriko.dto.request.CreateOrderRequest;
import uz.tabriko.dto.request.RejectOrderRequest;
import uz.tabriko.dto.response.OrderResponse;
import uz.tabriko.infrastructure.media.MediaStorageService;
import uz.tabriko.infrastructure.payment.PaymentGateway;
import uz.tabriko.infrastructure.payment.PaymentResult;
import uz.tabriko.repository.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepo;
    @Mock DeliveryRepository deliveryRepo;
    @Mock WalletTransactionRepository walletTxRepo;
    @Mock CreatorProfileRepository creatorProfileRepo;
    @Mock UserRepository userRepo;
    @Mock PaymentGateway paymentGateway;
    @Mock MediaStorageService mediaStorage;
    @Mock NotificationService notificationService;
    @Mock UserMapper mapper;

    @InjectMocks OrderService orderService;

    private UUID clientId, creatorId, orderId;
    private User client, creator;
    private Order order;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "commissionPercent", 15);

        clientId = UUID.randomUUID();
        creatorId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        client = new User();
        client.setId(clientId);
        client.setName("Client");
        client.setRole(Role.CLIENT);

        creator = new User();
        creator.setId(creatorId);
        creator.setName("Creator");
        creator.setRole(Role.CREATOR);

        order = new Order();
        order.setId(orderId);
        order.setClient(client);
        order.setCreator(creator);
        order.setPrice(new BigDecimal("100.00"));
        order.setStatus(OrderStatus.PENDING);
        order.setDeadline(Instant.now().plusSeconds(86400));
    }

    // ===== CREATE ORDER — B1: Hold must be exactly 100% of price =====

    @Test
    void createOrder_holdsExactly100Percent_notMore() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCreatorId(creatorId);
        req.setType(OrderType.VIDEO);
        req.setOption(OrderOption.SHER);

        CreatorProfile profile = new CreatorProfile();
        profile.setUser(creator);
        profile.setPriceFrom(new BigDecimal("100.00"));
        profile.setDeliveryDays(3);

        when(userRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(userRepo.findById(creatorId)).thenReturn(Optional.of(creator));
        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(profile));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.hold(any(), any(), any())).thenReturn(PaymentResult.ok("mock-tx"));
        when(mapper.toOrderResponse(any(), any())).thenReturn(new OrderResponse());

        orderService.createOrder(clientId, req);

        // Verify hold called with EXACT price (100.00), not 115.00 — B1 regression
        ArgumentCaptor<BigDecimal> amountCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paymentGateway).hold(eq(clientId), amountCap.capture(), any());
        assertThat(amountCap.getValue()).isEqualByComparingTo("100.00");

        // Verify HOLD transaction recorded with exact amount
        ArgumentCaptor<WalletTransaction> txCap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTxRepo).save(txCap.capture());
        WalletTransaction holdTx = txCap.getValue();
        assertThat(holdTx.getType()).isEqualTo(TransactionType.HOLD);
        assertThat(holdTx.getAmount()).isEqualByComparingTo("100.00");
        assertThat(holdTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    // ===== ACCEPT ORDER — creator gets 85%, no extra client COMMISSION debit =====

    @Test
    void acceptOrder_releasesExactly85PercentToCreator() {
        order.setStatus(OrderStatus.DELIVERED);
        Delivery delivery = buildDelivery(order);

        when(orderRepo.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveryRepo.findByOrderId(orderId)).thenReturn(Optional.of(delivery));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.release(any(), any(), any())).thenReturn(PaymentResult.ok("mock-tx"));
        when(mapper.toOrderResponse(any(), any())).thenReturn(new OrderResponse());

        orderService.acceptOrder(clientId, orderId);

        ArgumentCaptor<BigDecimal> releaseCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paymentGateway).release(eq(creatorId), releaseCap.capture(), eq(orderId));
        // 100 - 15% = 85.00
        assertThat(releaseCap.getValue()).isEqualByComparingTo("85.00");

        // Verify only ONE wallet transaction saved: RELEASE for creator (no COMMISSION debit on client)
        ArgumentCaptor<WalletTransaction> txCap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTxRepo, times(1)).save(txCap.capture());
        WalletTransaction releaseTx = txCap.getValue();
        assertThat(releaseTx.getType()).isEqualTo(TransactionType.RELEASE);
        assertThat(releaseTx.getAmount()).isEqualByComparingTo("85.00");
        assertThat(releaseTx.getUser().getId()).isEqualTo(creatorId);
    }

    @Test
    void acceptOrder_noExtraCommissionDebitOnClient() {
        order.setStatus(OrderStatus.DELIVERED);
        Delivery delivery = buildDelivery(order);

        when(orderRepo.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(deliveryRepo.findByOrderId(orderId)).thenReturn(Optional.of(delivery));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.release(any(), any(), any())).thenReturn(PaymentResult.ok("mock-tx"));
        when(mapper.toOrderResponse(any(), any())).thenReturn(new OrderResponse());

        orderService.acceptOrder(clientId, orderId);

        // Capture all saved transactions — there must be NO COMMISSION type for client
        ArgumentCaptor<WalletTransaction> txCap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTxRepo, atLeastOnce()).save(txCap.capture());
        boolean hasClientCommission = txCap.getAllValues().stream()
            .anyMatch(tx -> tx.getType() == TransactionType.COMMISSION
                         && tx.getUser().getId().equals(clientId));
        assertThat(hasClientCommission)
            .as("Client must NOT have extra COMMISSION debit on ACCEPT (B1 regression)")
            .isFalse();
    }

    @Test
    void acceptOrder_failsIfNotDelivered() {
        order.setStatus(OrderStatus.PENDING);
        when(orderRepo.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.acceptOrder(clientId, orderId))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void acceptOrder_forbiddenForNonClient() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepo.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> orderService.acceptOrder(stranger, orderId))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Not your order");
    }

    // ===== REJECT ORDER — full refund to client =====

    @Test
    void rejectOrder_refundsFullPriceToClient() {
        order.setStatus(OrderStatus.DELIVERED);
        RejectOrderRequest req = new RejectOrderRequest();
        req.setReason("Not what I expected");

        when(orderRepo.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.refund(any(), any(), any())).thenReturn(PaymentResult.ok("mock-tx"));
        when(deliveryRepo.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(mapper.toOrderResponse(any(), any())).thenReturn(new OrderResponse());

        orderService.rejectOrder(clientId, orderId, req);

        ArgumentCaptor<BigDecimal> refundCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paymentGateway).refund(eq(clientId), refundCap.capture(), eq(orderId));
        assertThat(refundCap.getValue()).isEqualByComparingTo("100.00");

        ArgumentCaptor<WalletTransaction> txCap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTxRepo).save(txCap.capture());
        assertThat(txCap.getValue().getType()).isEqualTo(TransactionType.REFUND);
        assertThat(txCap.getValue().getAmount()).isEqualByComparingTo("100.00");
        assertThat(txCap.getValue().getUser().getId()).isEqualTo(clientId);
    }

    // ===== OVERDUE — auto refund =====

    @Test
    void processOverdueOrders_refundsAndSetsRefundedStatus() {
        order.setStatus(OrderStatus.PENDING);

        when(orderRepo.findOverdueOrders(eq(OrderStatus.PENDING), any()))
            .thenReturn(List.of(order));
        when(orderRepo.findOverdueOrders(eq(OrderStatus.IN_PROGRESS), any()))
            .thenReturn(List.of());
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.refund(any(), any(), any())).thenReturn(PaymentResult.ok("mock-tx"));

        orderService.processOverdueOrders();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(paymentGateway).refund(eq(clientId), eq(new BigDecimal("100.00")), eq(orderId));

        ArgumentCaptor<WalletTransaction> txCap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTxRepo).save(txCap.capture());
        assertThat(txCap.getValue().getType()).isEqualTo(TransactionType.REFUND);
    }

    // ===== PRIVACY / CONSENT =====

    @Test
    void updatePrivacy_changesPublicFlag() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.updatePrivacy(clientId, orderId, true);

        assertThat(order.isPublic()).isTrue();
    }

    @Test
    void updatePrivacy_forbiddenForNonOwner() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updatePrivacy(UUID.randomUUID(), orderId, true))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Not your order");
    }

    @Test
    void updateConsent_changesPortfolioConsent() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.updateConsent(clientId, orderId, true);

        assertThat(order.isPortfolioConsent()).isTrue();
    }

    @Test
    void updateConsent_forbiddenForNonOwner() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateConsent(UUID.randomUUID(), orderId, true))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Not your order");
    }

    // ===== helpers =====

    private Delivery buildDelivery(Order order) {
        Delivery d = new Delivery();
        d.setOrder(order);
        d.setMediaUrlClean("clean://media");
        d.setMediaUrlWatermarked("watermarked://media");
        d.setWatermarked(true);
        return d;
    }
}
