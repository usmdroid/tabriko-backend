package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.Category;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.Order;
import uz.tabriko.domain.entity.PlatformSettingsEntity;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.WalletTransaction;
import uz.tabriko.domain.enums.NotificationType;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.domain.enums.ReportStatus;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.TransactionStatus;
import uz.tabriko.domain.enums.TransactionType;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.dto.request.AddCreatorRequest;
import uz.tabriko.dto.response.*;
import uz.tabriko.infrastructure.payment.PaymentGateway;
import uz.tabriko.repository.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepo;
    private final CreatorProfileRepository creatorProfileRepo;
    private final CategoryRepository categoryRepo;
    private final OrderRepository orderRepo;
    private final PortfolioItemRepository portfolioRepo;
    private final ReportRepository reportRepo;
    private final PlatformSettingsRepository settingsRepo;
    private final WalletTransactionRepository walletTxRepo;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final UserMapper mapper;

    @Transactional
    public CreatorResponse addCreator(AddCreatorRequest req) {
        User user = userRepo.findByPhone(req.getPhone()).orElseGet(() -> {
            User u = new User();
            u.setPhone(req.getPhone());
            u.setName(req.getName());
            u.setRole(Role.CREATOR);
            u.setStatus(UserStatus.ACTIVE);
            return userRepo.save(u);
        });
        user.setRole(Role.CREATOR);
        userRepo.save(user);

        Category category = categoryRepo.findById(req.getCategoryId())
                .orElseThrow(() -> ApiException.notFound("Category not found"));

        CreatorProfile cp = creatorProfileRepo.findByUserId(user.getId()).orElse(new CreatorProfile());
        cp.setUser(user);
        cp.setCategory(category);
        cp.setBio(req.getBio());
        cp.setPriceFrom(req.getPriceFrom() != null ? req.getPriceFrom() : BigDecimal.ZERO);
        cp.setDeliveryDays(req.getDeliveryDays());
        cp.setVerified(false);
        cp.setTier(req.getTier() != null ? req.getTier() : uz.tabriko.domain.enums.CreatorTier.STANDARD);
        creatorProfileRepo.save(cp);

        return mapper.toCreatorResponse(cp, portfolioRepo.findPublicWithConsent(user.getId()));
    }

    @Transactional
    public CreatorResponse verifyCreator(UUID creatorId) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        cp.setVerified(true);
        creatorProfileRepo.save(cp);
        return mapper.toCreatorResponse(cp, portfolioRepo.findPublicWithConsent(creatorId));
    }

    public List<CreatorResponse> getAllCreators() {
        return creatorProfileRepo.findAll().stream()
                .map(cp -> mapper.toCreatorResponse(cp, portfolioRepo.findPublicWithConsent(cp.getUserId())))
                .collect(Collectors.toList());
    }

    // Returns a page of all orders; frontend reads .content
    public PageResponse<OrderResponse> getAllOrders(int page, int size) {
        return PageResponse.of(
                orderRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)),
                o -> mapper.toOrderResponse(o, null)
        );
    }

    // --- Users ---

    public List<AdminUserResponse> getUsers(String search, String statusParam) {
        String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String pattern = (normalizedSearch != null) ? "%" + normalizedSearch.toLowerCase() + "%" : null;

        UserStatus status = null;
        if (statusParam != null && !statusParam.isBlank()) {
            try {
                status = UserStatus.valueOf(statusParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Invalid status: " + statusParam);
            }
        }

        return userRepo.findClientsFiltered(normalizedSearch, pattern, status)
                .stream()
                .map(this::toAdminUserResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void blockUser(UUID id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getRole() != Role.CLIENT) {
            throw ApiException.badRequest("Block/unblock is only allowed for CLIENT users");
        }
        user.setStatus(UserStatus.BLOCKED);
        userRepo.save(user);
    }

    @Transactional
    public void unblockUser(UUID id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getRole() != Role.CLIENT) {
            throw ApiException.badRequest("Block/unblock is only allowed for CLIENT users");
        }
        user.setStatus(UserStatus.ACTIVE);
        userRepo.save(user);
    }

    // --- Stats ---

    public AdminStatsResponse getStats() {
        AdminStatsResponse r = new AdminStatsResponse();
        r.setRevenue(orderRepo.sumPriceByStatus(OrderStatus.ACCEPTED));
        r.setActiveCreators(creatorProfileRepo.countActiveCreators(UserStatus.ACTIVE));
        r.setTotalUsers(userRepo.countByRole(Role.CLIENT));
        r.setPendingOrders(orderRepo.countByStatus(OrderStatus.PENDING));
        r.setTotalOrders(orderRepo.count());
        r.setModerationQueue(reportRepo.countByStatus(ReportStatus.OPEN));
        return r;
    }

    // --- Settings ---

    public PlatformSettings getSettings() {
        PlatformSettingsEntity entity = settingsRepo.findById(1)
                .orElseGet(() -> settingsRepo.save(new PlatformSettingsEntity()));
        return toPlatformSettings(entity);
    }

    @Transactional
    public PlatformSettings updateSettings(PlatformSettings dto) {
        PlatformSettingsEntity entity = settingsRepo.findById(1)
                .orElseGet(PlatformSettingsEntity::new);
        entity.setId(1);
        if (dto.getOrdersOpen() != null) entity.setOrdersOpen(dto.getOrdersOpen());
        if (dto.getMaintenanceMode() != null) entity.setMaintenanceMode(dto.getMaintenanceMode());
        if (dto.getRegistrationOpen() != null) entity.setRegistrationOpen(dto.getRegistrationOpen());
        settingsRepo.save(entity);
        return toPlatformSettings(entity);
    }

    // --- Creator flags ---

    @Transactional
    public CreatorResponse flagCreator(UUID creatorId, String flag) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        switch (flag.toLowerCase()) {
            case "top" -> cp.setTop(true);
            case "exclusive" -> cp.setExclusive(true);
            default -> throw ApiException.badRequest("Invalid flag: " + flag + ". Allowed: top, exclusive");
        }
        creatorProfileRepo.save(cp);
        return mapper.toCreatorResponse(cp, portfolioRepo.findPublicWithConsent(creatorId));
    }

    // --- Order refund ---

    @Transactional
    public void refundOrder(UUID orderId) {
        Order order = orderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.getStatus() == OrderStatus.REFUNDED || order.getStatus() == OrderStatus.REJECTED) {
            throw ApiException.badRequest("Order is already " + order.getStatus().name().toLowerCase());
        }
        order.setStatus(OrderStatus.REFUNDED);
        orderRepo.save(order);

        paymentGateway.refund(order.getClient().getId(), order.getPrice(), orderId);

        WalletTransaction tx = new WalletTransaction();
        tx.setUser(order.getClient());
        tx.setAmount(order.getPrice());
        tx.setType(TransactionType.REFUND);
        tx.setOrder(order);
        tx.setStatus(TransactionStatus.COMPLETED);
        walletTxRepo.save(tx);

        notificationService.sendNotification(
                order.getClient().getId(),
                "Order refunded",
                "Your order has been refunded by the admin.",
                NotificationType.ORDER_REFUNDED
        );
    }

    // --- Helpers ---

    private AdminUserResponse toAdminUserResponse(User u) {
        AdminUserResponse r = new AdminUserResponse();
        r.setId(u.getId());
        r.setName(u.getName());
        r.setPhone(u.getPhone());
        r.setStatus(u.getStatus().name().toLowerCase());
        r.setCreatedAt(u.getCreatedAt());
        return r;
    }

    private PlatformSettings toPlatformSettings(PlatformSettingsEntity e) {
        PlatformSettings r = new PlatformSettings();
        r.setOrdersOpen(e.isOrdersOpen());
        r.setMaintenanceMode(e.isMaintenanceMode());
        r.setRegistrationOpen(e.isRegistrationOpen());
        return r;
    }
}
