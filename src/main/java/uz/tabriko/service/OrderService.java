package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.*;
import uz.tabriko.domain.enums.*;
import uz.tabriko.dto.request.CreateOrderRequest;
import uz.tabriko.dto.request.DeliverOrderRequest;
import uz.tabriko.dto.request.RejectOrderRequest;
import uz.tabriko.dto.response.OrderResponse;
import uz.tabriko.dto.response.PageResponse;
import uz.tabriko.infrastructure.media.MediaStorageService;
import uz.tabriko.infrastructure.payment.PaymentGateway;
import uz.tabriko.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepo;
    private final DeliveryRepository deliveryRepo;
    private final WalletTransactionRepository walletTxRepo;
    private final CreatorProfileRepository creatorProfileRepo;
    private final UserRepository userRepo;
    private final PaymentGateway paymentGateway;
    private final MediaStorageService mediaStorage;
    private final NotificationService notificationService;
    private final UserMapper mapper;

    @Value("${app.commission-percent:15}")
    private int commissionPercent;

    @Transactional
    public OrderResponse createOrder(UUID clientId, CreateOrderRequest req) {
        User client = userRepo.findById(clientId)
                .orElseThrow(() -> ApiException.notFound("Client not found"));
        User creator = userRepo.findById(req.getCreatorId())
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        if (creator.getRole() != Role.CREATOR) {
            throw ApiException.badRequest("Target user is not a creator");
        }
        CreatorProfile profile = creatorProfileRepo.findByUserId(creator.getId())
                .orElseThrow(() -> ApiException.notFound("Creator profile not found"));

        BigDecimal price = profile.getPriceFrom();
        Instant deadline = Instant.now().plus(profile.getDeliveryDays(), ChronoUnit.DAYS);

        Order order = new Order();
        order.setClient(client);
        order.setCreator(creator);
        order.setType(req.getType());
        order.setOption(req.getOption());
        order.setRecipientName(req.getRecipientName());
        order.setRecipientOccasion(req.getRecipientOccasion());
        order.setCustomText(req.getCustomText());
        order.setPublic(req.isPublic());
        order.setPrice(price);
        order.setStatus(OrderStatus.PENDING);
        order.setDeadline(deadline);
        orderRepo.save(order);

        // Hold payment immediately
        paymentGateway.hold(clientId, price, order.getId());
        recordTransaction(client, price, TransactionType.HOLD, order);

        notificationService.sendNotification(
                creator.getId(),
                "New order",
                "You received a new order from " + client.getName(),
                NotificationType.ORDER_RECEIVED
        );

        return mapper.toOrderResponse(order, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(UUID userId, String role, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var orders = "CREATOR".equals(role)
                ? orderRepo.findByCreatorIdOrderByCreatedAtDesc(userId, pageable)
                : orderRepo.findByClientIdOrderByCreatedAtDesc(userId, pageable);

        List<UUID> orderIds = orders.getContent().stream().map(Order::getId).collect(java.util.stream.Collectors.toList());
        Map<UUID, Delivery> deliveryByOrderId = orderIds.isEmpty() ? Map.of()
                : deliveryRepo.findByOrderIdIn(orderIds).stream()
                        .collect(java.util.stream.Collectors.toMap(d -> d.getOrder().getId(), d -> d));

        return PageResponse.of(orders, o -> mapper.toOrderResponse(o, deliveryByOrderId.get(o.getId())));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID userId, UUID orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        boolean isOwner = order.getClient().getId().equals(userId) || order.getCreator().getId().equals(userId);
        if (!isOwner) throw ApiException.forbidden("Not your order");
        Delivery delivery = deliveryRepo.findByOrderId(orderId).orElse(null);
        return mapper.toOrderResponse(order, delivery);
    }

    @Transactional
    public OrderResponse deliverOrder(UUID creatorId, UUID orderId, DeliverOrderRequest req) {
        CreatorProfile creatorProfile = creatorProfileRepo.findByUserId(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator profile not found"));
        if (!creatorProfile.isProfileComplete()) {
            throw ApiException.conflict("CREATOR_PROFILE_INCOMPLETE");
        }

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getCreator().getId().equals(creatorId)) {
            throw ApiException.forbidden("Not your order");
        }
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.IN_PROGRESS) {
            throw ApiException.badRequest("Order cannot be delivered in current status");
        }

        String watermarked = mediaStorage.applyWatermark(req.getMediaUrl());

        Delivery delivery = deliveryRepo.findByOrderId(orderId).orElse(new Delivery());
        delivery.setOrder(order);
        delivery.setMediaUrlClean(req.getMediaUrl());
        delivery.setMediaUrlWatermarked(watermarked);
        delivery.setWatermarked(true);
        delivery.setDeliveredAt(Instant.now());
        deliveryRepo.save(delivery);

        order.setStatus(OrderStatus.DELIVERED);
        orderRepo.save(order);

        notificationService.sendNotification(
                order.getClient().getId(),
                "Order delivered",
                "Your order from " + order.getCreator().getName() + " is ready",
                NotificationType.ORDER_DELIVERED
        );

        return mapper.toOrderResponse(order, delivery);
    }

    @Transactional
    public OrderResponse acceptOrder(UUID clientId, UUID orderId) {
        Order order = orderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getClient().getId().equals(clientId)) {
            throw ApiException.forbidden("Not your order");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw ApiException.badRequest("Order is not in DELIVERED state");
        }

        Delivery delivery = deliveryRepo.findByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("Delivery not found"));
        delivery.setWatermarked(false);
        deliveryRepo.save(delivery);

        order.setStatus(OrderStatus.ACCEPTED);
        orderRepo.save(order);

        // Release funds minus commission
        BigDecimal commission = order.getPrice()
                .multiply(BigDecimal.valueOf(commissionPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal creatorAmount = order.getPrice().subtract(commission);

        paymentGateway.release(order.getCreator().getId(), creatorAmount, orderId);
        recordTransaction(order.getCreator(), creatorAmount, TransactionType.RELEASE, order);
        // Client's HOLD already covered the full price; no additional debit needed.

        notificationService.sendNotification(
                order.getCreator().getId(),
                "Order accepted",
                "Your greeting was accepted. Funds have been released.",
                NotificationType.ORDER_ACCEPTED
        );

        return mapper.toOrderResponse(order, delivery);
    }

    @Transactional
    public OrderResponse rejectOrder(UUID clientId, UUID orderId, RejectOrderRequest req) {
        Order order = orderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getClient().getId().equals(clientId)) {
            throw ApiException.forbidden("Not your order");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw ApiException.badRequest("Order is not in DELIVERED state");
        }

        order.setStatus(OrderStatus.REJECTED);
        order.setRejectionReason(req.getReason());
        orderRepo.save(order);

        // Refund client
        paymentGateway.refund(clientId, order.getPrice(), orderId);
        recordTransaction(order.getClient(), order.getPrice(), TransactionType.REFUND, order);

        notificationService.sendNotification(
                order.getCreator().getId(),
                "Order rejected",
                "Client rejected your greeting: " + req.getReason(),
                NotificationType.ORDER_REJECTED
        );

        return mapper.toOrderResponse(order, deliveryRepo.findByOrderId(orderId).orElse(null));
    }

    // Auto-refund orders that passed deadline without delivery
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void processOverdueOrders() {
        List<Order> overdue = new java.util.ArrayList<>(orderRepo.findOverdueOrders(OrderStatus.PENDING, Instant.now()));
        overdue.addAll(orderRepo.findOverdueOrders(OrderStatus.IN_PROGRESS, Instant.now()));
        for (Order order : overdue) {
            order.setStatus(OrderStatus.REFUNDED);
            orderRepo.save(order);
            paymentGateway.refund(order.getClient().getId(), order.getPrice(), order.getId());
            recordTransaction(order.getClient(), order.getPrice(), TransactionType.REFUND, order);
            notificationService.sendNotification(
                    order.getClient().getId(),
                    "Order refunded",
                    "Your order was not delivered on time. Full refund issued.",
                    NotificationType.ORDER_REFUNDED
            );
        }
    }

    @Transactional
    public void updatePrivacy(UUID clientId, UUID orderId, boolean isPublic) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getClient().getId().equals(clientId)) {
            throw ApiException.forbidden("Not your order");
        }
        order.setPublic(isPublic);
        orderRepo.save(order);
    }

    @Transactional
    public void updateConsent(UUID clientId, UUID orderId, boolean portfolioConsent) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getClient().getId().equals(clientId)) {
            throw ApiException.forbidden("Not your order");
        }
        order.setPortfolioConsent(portfolioConsent);
        orderRepo.save(order);
    }

    private void recordTransaction(User user, BigDecimal amount, TransactionType type, Order order) {
        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setAmount(amount);
        tx.setType(type);
        tx.setOrder(order);
        tx.setStatus(TransactionStatus.COMPLETED);
        walletTxRepo.save(tx);
    }
}
