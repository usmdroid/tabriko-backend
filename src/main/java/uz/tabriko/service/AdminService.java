package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.common.util.PhoneUtil;
import uz.tabriko.domain.entity.Category;
import uz.tabriko.domain.entity.CreatorContact;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.Order;
import uz.tabriko.domain.entity.PlatformSettingsEntity;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.WalletTransaction;
import uz.tabriko.domain.entity.UserDevice;
import uz.tabriko.domain.enums.NotificationType;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.domain.enums.ReportStatus;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.TransactionStatus;
import uz.tabriko.domain.enums.TransactionType;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.dto.request.AddCreatorContactRequest;
import uz.tabriko.dto.request.AddCreatorRequest;
import uz.tabriko.dto.request.AdminCategoryRequest;
import uz.tabriko.dto.request.UserNotifyRequest;
import uz.tabriko.dto.response.*;
import uz.tabriko.infrastructure.firebase.PushNotificationService;
import uz.tabriko.infrastructure.payment.PaymentGateway;
import uz.tabriko.repository.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final int MAX_ADMIN_CREATORS_PAGE_SIZE = 200;

    private final UserRepository userRepo;
    private final UserDeviceRepository userDeviceRepo;
    private final CreatorProfileRepository creatorProfileRepo;
    private final CategoryRepository categoryRepo;
    private final OrderRepository orderRepo;
    private final PortfolioItemRepository portfolioRepo;
    private final ReportRepository reportRepo;
    private final PlatformSettingsRepository settingsRepo;
    private final WalletTransactionRepository walletTxRepo;
    private final CreatorContactRepository contactRepo;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final PushNotificationService pushService;
    private final UserMapper mapper;

    @Transactional
    public CreatorResponse addCreator(AddCreatorRequest req) {
        String phone = PhoneUtil.normalize(req.getPhone());
        User user = userRepo.findByPhone(phone).orElseGet(() -> {
            User u = new User();
            u.setPhone(phone);
            u.setName(req.getName());
            u.setRole(Role.CREATOR);
            u.setStatus(UserStatus.ACTIVE);
            return userRepo.save(u);
        });
        user.setRole(Role.CREATOR);
        userRepo.save(user);

        Category category = categoryRepo.findById(req.getCategoryId())
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        if (category.isArchived()) throw ApiException.badRequest("Cannot assign an archived category");

        CreatorProfile cp = creatorProfileRepo.findByUserId(user.getId()).orElse(new CreatorProfile());
        cp.setUser(user);
        cp.setCategory(category);
        cp.setBio(req.getBio());
        cp.setPriceFrom(req.getPriceFrom() != null ? req.getPriceFrom() : BigDecimal.ZERO);
        cp.setDeliveryDays(req.getDeliveryDays());
        cp.setVerified(false);
        cp.setTier(req.getTier() != null ? req.getTier() : uz.tabriko.domain.enums.CreatorTier.STANDARD);
        if (req.getPassportSeries() != null) cp.setPassportSeries(req.getPassportSeries());
        if (req.getPassportNumber() != null) cp.setPassportNumber(req.getPassportNumber());
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

    @Transactional(readOnly = true)
    public List<CreatorResponse> getAllCreators() {
        return getAllCreators(0, MAX_ADMIN_CREATORS_PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public List<CreatorResponse> getAllCreators(int page, int size) {
        int boundedSize = Math.min(size, MAX_ADMIN_CREATORS_PAGE_SIZE);
        Page<CreatorProfile> profiles = creatorProfileRepo.findAllWithUser(PageRequest.of(page, boundedSize));
        List<CreatorProfile> content = profiles.getContent();
        if (content.isEmpty()) {
            return List.of();
        }
        List<UUID> creatorIds = content.stream().map(CreatorProfile::getUserId).collect(Collectors.toList());
        Map<UUID, List<uz.tabriko.domain.entity.PortfolioItem>> portfolioByCreator =
                portfolioRepo.findPublicWithConsentByCreatorIds(creatorIds).stream()
                        .collect(Collectors.groupingBy(p -> p.getCreator().getId()));
        Map<UUID, List<CreatorContact>> contactsByCreator =
                contactRepo.findByCreatorIdIn(creatorIds).stream()
                        .collect(Collectors.groupingBy(CreatorContact::getCreatorId));
        return content.stream()
                .map(cp -> {
                    CreatorResponse r = mapper.toCreatorResponse(cp,
                            portfolioByCreator.getOrDefault(cp.getUserId(), List.of()));
                    r.setContacts(mapper.toContactResponses(
                            contactsByCreator.getOrDefault(cp.getUserId(), List.of())));
                    return r;
                })
                .collect(Collectors.toList());
    }

    // Returns a page of all orders; frontend reads .content
    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(UUID id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        List<UserDevice> devices = userDeviceRepo.findByUserId(id);
        List<AdminUserDetailResponse.DeviceSummary> deviceSummaries = devices.stream()
                .map(d -> new AdminUserDetailResponse.DeviceSummary(
                        d.getId(),
                        d.getPlatform().name(),
                        d.getAppVersion(),
                        d.getDeviceName(),
                        d.getOsVersion(),
                        d.getUpdatedAt()
                ))
                .collect(Collectors.toList());
        return new AdminUserDetailResponse(
                user.getId(),
                user.getName(),
                user.getPhone(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getCreatedAt(),
                deviceSummaries
        );
    }

    @Transactional
    public void notifyUser(UUID userId, UserNotifyRequest req) {
        userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        List<UserDevice> allDevices = userDeviceRepo.findByUserId(userId);
        List<UserDevice> targetDevices;
        if (req.getDeviceIds() == null || req.getDeviceIds().isEmpty()) {
            targetDevices = allDevices;
        } else {
            targetDevices = allDevices.stream()
                    .filter(d -> req.getDeviceIds().contains(d.getId()))
                    .collect(Collectors.toList());
        }

        notificationService.createInAppNotification(userId, req.getTitle(), req.getBody(), NotificationType.SYSTEM);

        Map<String, String> data = new HashMap<>();
        data.put("type", NotificationType.SYSTEM.name());
        for (UserDevice device : targetDevices) {
            try {
                pushService.sendPush(device.getFcmToken(), req.getTitle(), req.getBody(), data);
            } catch (PushNotificationService.DeadTokenException e) {
                userDeviceRepo.deleteByFcmToken(e.getMessage());
            }
        }
    }

    // --- Stats ---

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public PlatformSettings getSettings() {
        // Read-only: return in-memory defaults if no row exists yet.
        // Persisting the default row is handled by updateSettings, the write path.
        PlatformSettingsEntity entity = settingsRepo.findById(1)
                .orElseGet(PlatformSettingsEntity::new);
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

    // --- Categories ---

    public List<AdminCategoryResponse> getAdminCategories() {
        return categoryRepo.findAll().stream()
                .map(mapper::toAdminCategoryResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AdminCategoryResponse createCategory(AdminCategoryRequest req) {
        Category cat = new Category();
        cat.setName(req.getNameUz());
        cat.setNameRu(req.getNameRu());
        cat.setNameEn(req.getNameEn());
        cat.setIconUrl(req.getIconUrl());
        return mapper.toAdminCategoryResponse(categoryRepo.save(cat));
    }

    @Transactional
    public AdminCategoryResponse updateCategory(Long id, AdminCategoryRequest req) {
        Category cat = categoryRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        cat.setName(req.getNameUz());
        cat.setNameRu(req.getNameRu());
        cat.setNameEn(req.getNameEn());
        if (req.getIconUrl() != null) cat.setIconUrl(req.getIconUrl());
        return mapper.toAdminCategoryResponse(categoryRepo.save(cat));
    }

    @Transactional
    public void archiveCategory(Long id) {
        Category cat = categoryRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        cat.setArchived(true);
        categoryRepo.save(cat);
    }

    @Transactional
    public void restoreCategory(Long id) {
        Category cat = categoryRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        cat.setArchived(false);
        categoryRepo.save(cat);
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

    // --- Creator contacts ---

    @Transactional(readOnly = true)
    public CreatorResponse getCreatorDetail(UUID creatorId) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        CreatorResponse r = mapper.toCreatorResponse(cp, portfolioRepo.findPublicWithConsent(creatorId));
        r.setContacts(mapper.toContactResponses(contactRepo.findByCreatorIdOrderByCreatedAtAsc(creatorId)));
        return r;
    }

    @Transactional
    public CreatorContactResponse addCreatorContact(UUID creatorId, AddCreatorContactRequest req) {
        creatorProfileRepo.findByUserId(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        String phone = PhoneUtil.normalize(req.getPhone());
        if (contactRepo.existsByCreatorIdAndPhone(creatorId, phone)) {
            throw ApiException.badRequest("Contact with this phone already exists");
        }
        CreatorContact contact = new CreatorContact();
        contact.setCreatorId(creatorId);
        contact.setPhone(phone);
        contact.setLabel(req.getLabel());
        CreatorContact saved = contactRepo.save(contact);
        return mapper.toContactResponse(saved);
    }

    @Transactional
    public void deleteCreatorContact(UUID creatorId, UUID contactId) {
        CreatorContact contact = contactRepo.findByIdAndCreatorId(contactId, creatorId)
                .orElseThrow(() -> ApiException.notFound("Contact not found"));
        contactRepo.delete(contact);
    }

    // --- Order refund ---

    @Transactional
    public void refundOrder(UUID orderId) {
        Order order = orderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.getStatus() == OrderStatus.REFUNDED || order.getStatus() == OrderStatus.REJECTED) {
            throw ApiException.badRequest("Order is already " + order.getStatus().name().toLowerCase());
        }
        if (order.getStatus() == OrderStatus.ACCEPTED) {
            // Funds were already released to the creator (minus commission) on accept.
            // Refunding the client on top of that would pay out twice for the same order.
            throw ApiException.badRequest("Order already accepted; funds were released to the creator and cannot be refunded");
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
