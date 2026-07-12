package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.Category;
import uz.tabriko.domain.entity.CreatorContact;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.PlatformSettingsEntity;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.CreatorTier;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.domain.enums.ReportStatus;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.domain.entity.UserDevice;
import uz.tabriko.domain.enums.Platform;
import uz.tabriko.dto.request.AddCreatorContactRequest;
import uz.tabriko.dto.request.AddCreatorRequest;
import uz.tabriko.dto.request.UserNotifyRequest;
import uz.tabriko.dto.response.AdminStatsResponse;
import uz.tabriko.dto.response.AdminUserDetailResponse;
import uz.tabriko.dto.response.AdminUserResponse;
import uz.tabriko.dto.response.CreatorContactResponse;
import uz.tabriko.dto.response.CreatorResponse;
import uz.tabriko.dto.response.PlatformSettings;
import uz.tabriko.infrastructure.firebase.PushNotificationService;
import uz.tabriko.repository.*;

import org.springframework.data.domain.Page;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock UserRepository userRepo;
    @Mock UserDeviceRepository userDeviceRepo;
    @Mock CreatorProfileRepository creatorProfileRepo;
    @Mock CategoryRepository categoryRepo;
    @Mock OrderRepository orderRepo;
    @Mock PortfolioItemRepository portfolioRepo;
    @Mock ReportRepository reportRepo;
    @Mock PlatformSettingsRepository settingsRepo;
    @Mock uz.tabriko.repository.WalletTransactionRepository walletTxRepo;
    @Mock CreatorContactRepository contactRepo;
    @Mock uz.tabriko.infrastructure.payment.PaymentGateway paymentGateway;
    @Mock NotificationService notificationService;
    @Mock PushNotificationService pushService;
    @Mock UserMapper mapper;

    @InjectMocks AdminService adminService;

    private UUID clientId;
    private User clientUser;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        clientUser = new User();
        clientUser.setId(clientId);
        clientUser.setName("Test User");
        clientUser.setPhone("+998901234567");
        clientUser.setRole(Role.CLIENT);
        clientUser.setStatus(UserStatus.ACTIVE);
    }

    // ===== POST /admin/orders/{id}/refund — B1: must not double-pay an ACCEPTED order =====

    @Test
    void refundOrder_acceptedOrder_throwsBadRequest_doesNotRefund() {
        uz.tabriko.domain.entity.Order order = new uz.tabriko.domain.entity.Order();
        UUID orderId = UUID.randomUUID();
        order.setId(orderId);
        order.setClient(clientUser);
        order.setPrice(new java.math.BigDecimal("100.00"));
        order.setStatus(OrderStatus.ACCEPTED);

        when(orderRepo.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> adminService.refundOrder(orderId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already accepted");

        verify(paymentGateway, never()).refund(any(), any(), any());
        verify(walletTxRepo, never()).save(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void refundOrder_deliveredOrder_refundsClient() {
        uz.tabriko.domain.entity.Order order = new uz.tabriko.domain.entity.Order();
        UUID orderId = UUID.randomUUID();
        order.setId(orderId);
        order.setClient(clientUser);
        order.setPrice(new java.math.BigDecimal("100.00"));
        order.setStatus(OrderStatus.DELIVERED);

        when(orderRepo.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.refund(any(), any(), any()))
                .thenReturn(uz.tabriko.infrastructure.payment.PaymentResult.ok("mock-tx"));

        adminService.refundOrder(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(paymentGateway).refund(eq(clientId), eq(new java.math.BigDecimal("100.00")), eq(orderId));
        verify(walletTxRepo).save(any());
    }

    // ===== POST /admin/creators — addCreator stores tier =====

    @Test
    void addCreator_setsTier() {
        UUID creatorUserId = UUID.randomUUID();

        when(userRepo.findByPhone("+998901234567")).thenReturn(Optional.empty());
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(creatorUserId);
            return u;
        });

        Category cat = new Category();
        cat.setId(1L);
        when(categoryRepo.findById(1L)).thenReturn(Optional.of(cat));
        when(creatorProfileRepo.findByUserId(any())).thenReturn(Optional.empty());
        when(creatorProfileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(portfolioRepo.findPublicWithConsent(any())).thenReturn(List.of());

        AddCreatorRequest req = new AddCreatorRequest();
        req.setPhone("+998901234567");
        req.setName("Star Creator");
        req.setCategoryId(1L);
        req.setTier(CreatorTier.TOP);

        adminService.addCreator(req);

        ArgumentCaptor<CreatorProfile> cpCap = ArgumentCaptor.forClass(CreatorProfile.class);
        verify(creatorProfileRepo).save(cpCap.capture());
        assertThat(cpCap.getValue().getTier()).isEqualTo(CreatorTier.TOP);
    }

    @Test
    void addCreator_rawPhoneFormat_normalizesForLookupAndSave() {
        UUID creatorUserId = UUID.randomUUID();

        when(userRepo.findByPhone("+998901234567")).thenReturn(Optional.empty());
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(creatorUserId);
            return u;
        });

        Category cat = new Category();
        cat.setId(1L);
        when(categoryRepo.findById(1L)).thenReturn(Optional.of(cat));
        when(creatorProfileRepo.findByUserId(any())).thenReturn(Optional.empty());
        when(creatorProfileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(portfolioRepo.findPublicWithConsent(any())).thenReturn(List.of());

        AddCreatorRequest req = new AddCreatorRequest();
        req.setPhone("998 90 123-45-67");
        req.setName("Star Creator");
        req.setCategoryId(1L);

        adminService.addCreator(req);

        // Looked up and saved under the normalized phone, not the raw input
        verify(userRepo).findByPhone("+998901234567");
        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepo, atLeastOnce()).save(userCap.capture());
        assertThat(userCap.getAllValues()).allSatisfy(u -> assertThat(u.getPhone()).isEqualTo("+998901234567"));
    }

    // ===== GET /admin/users =====

    @Test
    void getUsers_callsFindClientsFiltered_returnsOnlyClientUsers() {
        when(userRepo.findClientsFiltered(any(), any(), any())).thenReturn(List.of(clientUser));

        List<AdminUserResponse> result = adminService.getUsers(null, null);

        assertThat(result).hasSize(1);
        // Verify the repo was called (CLIENT filtering is inside the JPQL query)
        verify(userRepo).findClientsFiltered(isNull(), isNull(), isNull());
    }

    @Test
    void getUsers_searchPassedAsPattern() {
        when(userRepo.findClientsFiltered(any(), any(), any())).thenReturn(List.of());

        adminService.getUsers("alice", null);

        ArgumentCaptor<String> searchCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> patternCap = ArgumentCaptor.forClass(String.class);
        verify(userRepo).findClientsFiltered(searchCap.capture(), patternCap.capture(), isNull());
        assertThat(searchCap.getValue()).isEqualTo("alice");
        assertThat(patternCap.getValue()).isEqualTo("%alice%");
    }

    @Test
    void getUsers_statusFilter_parsedCaseInsensitive() {
        when(userRepo.findClientsFiltered(any(), any(), eq(UserStatus.ACTIVE))).thenReturn(List.of());

        adminService.getUsers(null, "active");

        verify(userRepo).findClientsFiltered(isNull(), isNull(), eq(UserStatus.ACTIVE));
    }

    @Test
    void getUsers_invalidStatus_throwsBadRequest() {
        assertThatThrownBy(() -> adminService.getUsers(null, "UNKNOWN_STATUS"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid status");
    }

    @Test
    void getUsers_responseStatus_isLowercased() {
        when(userRepo.findClientsFiltered(any(), any(), any())).thenReturn(List.of(clientUser));

        List<AdminUserResponse> result = adminService.getUsers(null, null);

        assertThat(result.get(0).getStatus()).isEqualTo("active");

        clientUser.setStatus(UserStatus.BLOCKED);
        when(userRepo.findClientsFiltered(any(), any(), any())).thenReturn(List.of(clientUser));
        result = adminService.getUsers(null, null);
        assertThat(result.get(0).getStatus()).isEqualTo("blocked");
    }

    @Test
    void getUsers_response_doesNotLeakPasswordOrOtpFields() {
        // AdminUserResponse must not have any password or OTP fields
        Field[] fields = AdminUserResponse.class.getDeclaredFields();
        List<String> fieldNames = Arrays.stream(fields).map(Field::getName).toList();
        assertThat(fieldNames)
                .doesNotContain("password", "passwordHash", "otp", "otpCode", "otpHash", "otpExpiry");
    }

    // ===== POST /admin/users/{id}/block|unblock =====

    @Test
    void blockUser_clientUser_setsStatusBlocked() {
        when(userRepo.findById(clientId)).thenReturn(Optional.of(clientUser));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminService.blockUser(clientId);

        assertThat(clientUser.getStatus()).isEqualTo(UserStatus.BLOCKED);
        verify(userRepo).save(clientUser);
    }

    @Test
    void unblockUser_clientUser_setsStatusActive() {
        clientUser.setStatus(UserStatus.BLOCKED);
        when(userRepo.findById(clientId)).thenReturn(Optional.of(clientUser));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminService.unblockUser(clientId);

        assertThat(clientUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void blockUser_nonClientTarget_throwsBadRequest() {
        for (Role role : new Role[]{Role.MODERATOR, Role.SUPERADMIN, Role.CREATOR}) {
            clientUser.setRole(role);
            when(userRepo.findById(clientId)).thenReturn(Optional.of(clientUser));

            assertThatThrownBy(() -> adminService.blockUser(clientId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("CLIENT")
                    .withFailMessage("Expected rejection for role " + role);
        }
    }

    @Test
    void unblockUser_nonClientTarget_throwsBadRequest() {
        clientUser.setRole(Role.MODERATOR);
        when(userRepo.findById(clientId)).thenReturn(Optional.of(clientUser));

        assertThatThrownBy(() -> adminService.unblockUser(clientId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("CLIENT");
    }

    @Test
    void blockUser_unknownId_throwsNotFound() {
        when(userRepo.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.blockUser(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    // ===== GET /admin/stats =====

    @Test
    void getStats_revenue_sumsAcceptedOrderPrices() {
        when(orderRepo.sumPriceByStatus(OrderStatus.ACCEPTED)).thenReturn(new BigDecimal("12345.00"));
        when(creatorProfileRepo.countActiveCreators(any())).thenReturn(0L);
        when(userRepo.countByRole(any())).thenReturn(0L);
        when(orderRepo.countByStatus(any())).thenReturn(0L);
        when(orderRepo.count()).thenReturn(0L);
        when(reportRepo.countByStatus(any())).thenReturn(0L);

        AdminStatsResponse stats = adminService.getStats();

        assertThat(stats.getRevenue()).isEqualByComparingTo("12345.00");
        verify(orderRepo).sumPriceByStatus(OrderStatus.ACCEPTED);
    }

    @Test
    void getStats_activeCreators_isVerifiedAndActiveUsers() {
        when(orderRepo.sumPriceByStatus(any())).thenReturn(BigDecimal.ZERO);
        when(creatorProfileRepo.countActiveCreators(UserStatus.ACTIVE)).thenReturn(7L);
        when(userRepo.countByRole(any())).thenReturn(0L);
        when(orderRepo.countByStatus(any())).thenReturn(0L);
        when(orderRepo.count()).thenReturn(0L);
        when(reportRepo.countByStatus(any())).thenReturn(0L);

        AdminStatsResponse stats = adminService.getStats();

        assertThat(stats.getActiveCreators()).isEqualTo(7L);
        verify(creatorProfileRepo).countActiveCreators(UserStatus.ACTIVE);
    }

    @Test
    void getStats_totalUsers_isClientCount() {
        when(orderRepo.sumPriceByStatus(any())).thenReturn(BigDecimal.ZERO);
        when(creatorProfileRepo.countActiveCreators(any())).thenReturn(0L);
        when(userRepo.countByRole(Role.CLIENT)).thenReturn(42L);
        when(orderRepo.countByStatus(any())).thenReturn(0L);
        when(orderRepo.count()).thenReturn(5L);
        when(reportRepo.countByStatus(any())).thenReturn(0L);

        AdminStatsResponse stats = adminService.getStats();

        assertThat(stats.getTotalUsers()).isEqualTo(42L);
        verify(userRepo).countByRole(Role.CLIENT);
    }

    @Test
    void getStats_pendingOrders_countsPendingStatus() {
        when(orderRepo.sumPriceByStatus(any())).thenReturn(BigDecimal.ZERO);
        when(creatorProfileRepo.countActiveCreators(any())).thenReturn(0L);
        when(userRepo.countByRole(any())).thenReturn(0L);
        when(orderRepo.countByStatus(OrderStatus.PENDING)).thenReturn(3L);
        when(orderRepo.count()).thenReturn(10L);
        when(reportRepo.countByStatus(any())).thenReturn(0L);

        AdminStatsResponse stats = adminService.getStats();

        assertThat(stats.getPendingOrders()).isEqualTo(3L);
        assertThat(stats.getTotalOrders()).isEqualTo(10L);
    }

    @Test
    void getStats_moderationQueue_countsOpenReports() {
        when(orderRepo.sumPriceByStatus(any())).thenReturn(BigDecimal.ZERO);
        when(creatorProfileRepo.countActiveCreators(any())).thenReturn(0L);
        when(userRepo.countByRole(any())).thenReturn(0L);
        when(orderRepo.countByStatus(any())).thenReturn(0L);
        when(orderRepo.count()).thenReturn(0L);
        when(reportRepo.countByStatus(ReportStatus.OPEN)).thenReturn(5L);

        AdminStatsResponse stats = adminService.getStats();

        assertThat(stats.getModerationQueue()).isEqualTo(5L);
        verify(reportRepo).countByStatus(ReportStatus.OPEN);
    }

    // ===== PATCH /admin/settings — partial (boxed-Boolean fix) =====

    @Test
    void updateSettings_singleField_doesNotResetOthers() {
        PlatformSettingsEntity existing = new PlatformSettingsEntity();
        existing.setId(1);
        existing.setOrdersOpen(true);
        existing.setMaintenanceMode(false);
        existing.setRegistrationOpen(true);

        when(settingsRepo.findById(1)).thenReturn(Optional.of(existing));
        when(settingsRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Send only maintenanceMode=true; the other two must remain unchanged
        PlatformSettings patch = new PlatformSettings();
        patch.setMaintenanceMode(true);

        PlatformSettings result = adminService.updateSettings(patch);

        assertThat(result.getMaintenanceMode()).isTrue();
        assertThat(result.getOrdersOpen()).isTrue();       // unchanged
        assertThat(result.getRegistrationOpen()).isTrue(); // unchanged
    }

    @Test
    void updateSettings_allFieldsNull_changesNothing() {
        PlatformSettingsEntity existing = new PlatformSettingsEntity();
        existing.setId(1);
        existing.setOrdersOpen(false);
        existing.setMaintenanceMode(true);
        existing.setRegistrationOpen(false);

        when(settingsRepo.findById(1)).thenReturn(Optional.of(existing));
        when(settingsRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformSettings result = adminService.updateSettings(new PlatformSettings());

        assertThat(result.getOrdersOpen()).isFalse();
        assertThat(result.getMaintenanceMode()).isTrue();
        assertThat(result.getRegistrationOpen()).isFalse();
    }

    @Test
    void getSettings_returnsInMemoryDefaultWhenAbsent_withoutPersisting() {
        when(settingsRepo.findById(1)).thenReturn(Optional.empty());

        PlatformSettings result = adminService.getSettings();

        // getSettings is read-only: persisting a default row is updateSettings' job.
        assertThat(result).isNotNull();
        verify(settingsRepo, never()).save(any());
    }

    // ===== GET /admin/creators/{id} — getCreatorDetail =====

    @Test
    void getCreatorDetail_existingCreator_returnsDetailWithContacts() {
        UUID creatorId = UUID.randomUUID();
        CreatorProfile cp = new CreatorProfile();
        cp.setUser(clientUser);

        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(cp));
        when(portfolioRepo.findPublicWithConsent(creatorId)).thenReturn(List.of());

        CreatorResponse base = new CreatorResponse();
        base.setId(creatorId);
        base.setName("Star Creator");
        when(mapper.toCreatorResponse(eq(cp), anyList())).thenReturn(base);

        CreatorContact contact = new CreatorContact();
        contact.setId(UUID.randomUUID());
        contact.setCreatorId(creatorId);
        contact.setPhone("+998901234567");
        contact.setCreatedAt(Instant.now());
        when(contactRepo.findByCreatorIdOrderByCreatedAtAsc(creatorId)).thenReturn(List.of(contact));

        CreatorContactResponse contactResponse = new CreatorContactResponse();
        contactResponse.setId(contact.getId());
        contactResponse.setPhone(contact.getPhone());
        when(mapper.toContactResponses(anyList())).thenReturn(List.of(contactResponse));

        CreatorResponse result = adminService.getCreatorDetail(creatorId);

        assertThat(result.getId()).isEqualTo(creatorId);
        assertThat(result.getName()).isEqualTo("Star Creator");
        assertThat(result.getContacts()).hasSize(1);
        assertThat(result.getContacts().get(0).getPhone()).isEqualTo("+998901234567");
    }

    @Test
    void getCreatorDetail_notFound_throwsApiException() {
        UUID creatorId = UUID.randomUUID();
        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getCreatorDetail(creatorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    // ===== GET /admin/creators — getAllCreators returns contacts =====

    @Test
    @SuppressWarnings("unchecked")
    void getAllCreators_creatorWithContacts_returnsNonEmptyContacts() {
        UUID creatorId = UUID.randomUUID();
        User creatorUser = new User();
        creatorUser.setId(creatorId);
        creatorUser.setName("Star Creator");
        creatorUser.setPhone("+998901234567");
        creatorUser.setRole(Role.CREATOR);
        creatorUser.setStatus(UserStatus.ACTIVE);

        CreatorProfile cp = new CreatorProfile();
        cp.setUserId(creatorId);
        cp.setUser(creatorUser);

        Page<CreatorProfile> pageMock = mock(Page.class);
        when(pageMock.getContent()).thenReturn(List.of(cp));
        when(creatorProfileRepo.findAllWithUser(any())).thenReturn(pageMock);
        when(portfolioRepo.findPublicWithConsentByCreatorIds(any())).thenReturn(List.of());

        CreatorContact contact = new CreatorContact();
        contact.setId(UUID.randomUUID());
        contact.setCreatorId(creatorId);
        contact.setPhone("+998901234567");
        contact.setCreatedAt(Instant.now());
        when(contactRepo.findByCreatorIdIn(List.of(creatorId))).thenReturn(List.of(contact));

        CreatorResponse base = new CreatorResponse();
        base.setId(creatorId);
        when(mapper.toCreatorResponse(any(CreatorProfile.class), anyList())).thenReturn(base);

        CreatorContactResponse contactResp = new CreatorContactResponse();
        contactResp.setId(contact.getId());
        contactResp.setPhone(contact.getPhone());
        when(mapper.toContactResponses(anyList())).thenReturn(List.of(contactResp));

        List<CreatorResponse> result = adminService.getAllCreators(0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContacts()).hasSize(1);
        assertThat(result.get(0).getContacts().get(0).getPhone()).isEqualTo("+998901234567");
        verify(contactRepo).findByCreatorIdIn(List.of(creatorId));
    }

    // ===== POST /admin/creators/{id}/contacts — addCreatorContact =====

    @Test
    void addCreatorContact_creatorExists_savesAndReturnsContact() {
        UUID creatorId = UUID.randomUUID();
        CreatorProfile cp = new CreatorProfile();
        cp.setUser(clientUser);

        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(cp));
        when(contactRepo.existsByCreatorIdAndPhone(eq(creatorId), anyString())).thenReturn(false);

        CreatorContact saved = new CreatorContact();
        saved.setId(UUID.randomUUID());
        saved.setCreatorId(creatorId);
        saved.setPhone("+998901234567");
        saved.setCreatedAt(Instant.now());
        when(contactRepo.save(any(CreatorContact.class))).thenReturn(saved);

        CreatorContactResponse expected = new CreatorContactResponse();
        expected.setId(saved.getId());
        expected.setPhone(saved.getPhone());
        when(mapper.toContactResponse(saved)).thenReturn(expected);

        AddCreatorContactRequest req = new AddCreatorContactRequest();
        req.setPhone("+998901234567");
        req.setLabel("Home");

        CreatorContactResponse result = adminService.addCreatorContact(creatorId, req);

        assertThat(result.getPhone()).isEqualTo("+998901234567");
        verify(contactRepo).save(any(CreatorContact.class));
    }

    @Test
    void addCreatorContact_creatorNotFound_throwsNotFound() {
        UUID creatorId = UUID.randomUUID();
        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.empty());

        AddCreatorContactRequest req = new AddCreatorContactRequest();
        req.setPhone("+998901234567");

        assertThatThrownBy(() -> adminService.addCreatorContact(creatorId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");

        verify(contactRepo, never()).save(any());
    }

    @Test
    void addCreatorContact_duplicatePhone_throwsBadRequest() {
        UUID creatorId = UUID.randomUUID();
        CreatorProfile cp = new CreatorProfile();
        cp.setUser(clientUser);

        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(cp));
        when(contactRepo.existsByCreatorIdAndPhone(eq(creatorId), anyString())).thenReturn(true);

        AddCreatorContactRequest req = new AddCreatorContactRequest();
        req.setPhone("+998901234567");

        assertThatThrownBy(() -> adminService.addCreatorContact(creatorId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");

        verify(contactRepo, never()).save(any());
    }

    @Test
    void addCreatorContact_rawPhoneFormat_normalizesForLookupAndSave() {
        UUID creatorId = UUID.randomUUID();
        CreatorProfile cp = new CreatorProfile();
        cp.setUser(clientUser);

        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(cp));
        when(contactRepo.existsByCreatorIdAndPhone(eq(creatorId), eq("+998901234567"))).thenReturn(false);

        CreatorContact saved = new CreatorContact();
        saved.setId(UUID.randomUUID());
        saved.setCreatorId(creatorId);
        saved.setPhone("+998901234567");
        saved.setCreatedAt(Instant.now());
        when(contactRepo.save(any(CreatorContact.class))).thenReturn(saved);

        CreatorContactResponse expected = new CreatorContactResponse();
        expected.setPhone(saved.getPhone());
        when(mapper.toContactResponse(saved)).thenReturn(expected);

        AddCreatorContactRequest req = new AddCreatorContactRequest();
        req.setPhone("998 90 123-45-67");
        req.setLabel("Work");

        adminService.addCreatorContact(creatorId, req);

        verify(contactRepo).existsByCreatorIdAndPhone(creatorId, "+998901234567");
        ArgumentCaptor<CreatorContact> cap = ArgumentCaptor.forClass(CreatorContact.class);
        verify(contactRepo).save(cap.capture());
        assertThat(cap.getValue().getPhone()).isEqualTo("+998901234567");
    }

    // ===== DELETE /admin/creators/{id}/contacts/{contactId} =====

    @Test
    void deleteCreatorContact_wrongCreator_throwsNotFound() {
        UUID creatorIdA = UUID.randomUUID();
        UUID creatorIdB = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();

        // Contact belongs to creatorIdA, but we request deletion as creatorIdB
        when(contactRepo.findByIdAndCreatorId(contactId, creatorIdB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.deleteCreatorContact(creatorIdB, contactId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");

        verify(contactRepo, never()).delete(any());
    }

    @Test
    void deleteCreatorContact_ownContact_deletesSuccessfully() {
        UUID creatorId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();

        CreatorContact contact = new CreatorContact();
        contact.setId(contactId);
        contact.setCreatorId(creatorId);
        when(contactRepo.findByIdAndCreatorId(contactId, creatorId)).thenReturn(Optional.of(contact));

        adminService.deleteCreatorContact(creatorId, contactId);

        verify(contactRepo).delete(contact);
    }

    // ===== GET /admin/users/{id} — getUserDetail =====

    @Test
    void getUserDetail_existingUser_returnsDetailWithDevices() {
        UUID deviceId = UUID.randomUUID();
        UserDevice device = new UserDevice();
        device.setId(deviceId);
        device.setPlatform(Platform.ANDROID);
        device.setAppVersion("2.0.0");
        device.setDeviceName("Pixel 7");
        device.setOsVersion("Android 14");
        device.setUpdatedAt(Instant.now());

        when(userRepo.findById(clientId)).thenReturn(Optional.of(clientUser));
        when(userDeviceRepo.findByUserId(clientId)).thenReturn(List.of(device));

        AdminUserDetailResponse result = adminService.getUserDetail(clientId);

        assertThat(result.id()).isEqualTo(clientId);
        assertThat(result.name()).isEqualTo("Test User");
        assertThat(result.phone()).isEqualTo("+998901234567");
        assertThat(result.role()).isEqualTo("CLIENT");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.devices()).hasSize(1);
        AdminUserDetailResponse.DeviceSummary summary = result.devices().get(0);
        assertThat(summary.id()).isEqualTo(deviceId);
        assertThat(summary.platform()).isEqualTo("ANDROID");
        assertThat(summary.appVersion()).isEqualTo("2.0.0");
        assertThat(summary.deviceName()).isEqualTo("Pixel 7");
        assertThat(summary.osVersion()).isEqualTo("Android 14");
    }

    @Test
    void getUserDetail_unknownUser_throwsNotFound() {
        when(userRepo.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getUserDetail(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getUserDetail_noDevices_returnsEmptyDeviceList() {
        when(userRepo.findById(clientId)).thenReturn(Optional.of(clientUser));
        when(userDeviceRepo.findByUserId(clientId)).thenReturn(List.of());

        AdminUserDetailResponse result = adminService.getUserDetail(clientId);

        assertThat(result.devices()).isEmpty();
    }

    // ===== POST /admin/users/{id}/notify =====

    @Test
    void notifyUser_allDevices_pushesToAll() {
        UserDevice d1 = new UserDevice();
        d1.setId(UUID.randomUUID());
        d1.setFcmToken("token-1");
        UserDevice d2 = new UserDevice();
        d2.setId(UUID.randomUUID());
        d2.setFcmToken("token-2");

        when(userRepo.findById(clientId)).thenReturn(Optional.of(clientUser));
        when(userDeviceRepo.findByUserId(clientId)).thenReturn(List.of(d1, d2));

        UserNotifyRequest req = new UserNotifyRequest();
        req.setTitle("Hello");
        req.setBody("World");

        var result = adminService.notifyUser(clientId, req);

        verify(notificationService).createInAppNotification(eq(clientId), eq("Hello"), eq("World"),
                eq(uz.tabriko.domain.enums.NotificationType.SYSTEM));
        verify(pushService).sendPush(eq("token-1"), eq("Hello"), eq("World"), any());
        verify(pushService).sendPush(eq("token-2"), eq("Hello"), eq("World"), any());
        assertThat(result.getTargeted()).isEqualTo(2);
        assertThat(result.getDelivered()).isEqualTo(2);
        assertThat(result.getFailed()).isZero();
    }

    @Test
    void notifyUser_specificDeviceIds_pushesOnlyToSelected() {
        UUID selectedId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        UserDevice selected = new UserDevice();
        selected.setId(selectedId);
        selected.setFcmToken("token-selected");

        UserDevice other = new UserDevice();
        other.setId(otherId);
        other.setFcmToken("token-other");

        when(userRepo.findById(clientId)).thenReturn(Optional.of(clientUser));
        when(userDeviceRepo.findByUserId(clientId)).thenReturn(List.of(selected, other));

        UserNotifyRequest req = new UserNotifyRequest();
        req.setTitle("Hi");
        req.setBody("There");
        req.setDeviceIds(List.of(selectedId));

        adminService.notifyUser(clientId, req);

        verify(notificationService).createInAppNotification(eq(clientId), any(), any(), any());
        verify(pushService).sendPush(eq("token-selected"), any(), any(), any());
        verify(pushService, never()).sendPush(eq("token-other"), any(), any(), any());
    }

    @Test
    void notifyUser_deadToken_cleansUpAndContinues() {
        UserDevice dead = new UserDevice();
        dead.setId(UUID.randomUUID());
        dead.setFcmToken("dead-token");
        UserDevice alive = new UserDevice();
        alive.setId(UUID.randomUUID());
        alive.setFcmToken("alive-token");

        when(userRepo.findById(clientId)).thenReturn(Optional.of(clientUser));
        when(userDeviceRepo.findByUserId(clientId)).thenReturn(List.of(dead, alive));
        doThrow(new PushNotificationService.DeadTokenException("dead-token"))
                .when(pushService).sendPush(eq("dead-token"), any(), any(), any());

        UserNotifyRequest req = new UserNotifyRequest();
        req.setTitle("Test");
        req.setBody("Body");

        var result = adminService.notifyUser(clientId, req);

        verify(userDeviceRepo).deleteByFcmToken("dead-token");
        verify(pushService).sendPush(eq("alive-token"), any(), any(), any());
        assertThat(result.getTargeted()).isEqualTo(2);
        assertThat(result.getDelivered()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
    }

    @Test
    void notifyUser_unknownUser_throwsNotFound() {
        when(userRepo.findById(any())).thenReturn(Optional.empty());

        UserNotifyRequest req = new UserNotifyRequest();
        req.setTitle("Test");
        req.setBody("Body");

        assertThatThrownBy(() -> adminService.notifyUser(UUID.randomUUID(), req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");

        verify(pushService, never()).sendPush(any(), any(), any(), any());
        verify(notificationService, never()).createInAppNotification(any(), any(), any(), any());
    }
}
