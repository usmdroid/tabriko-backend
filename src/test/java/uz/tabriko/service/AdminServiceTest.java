package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.PlatformSettingsEntity;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.domain.enums.ReportStatus;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.dto.response.AdminStatsResponse;
import uz.tabriko.dto.response.AdminUserResponse;
import uz.tabriko.dto.response.PlatformSettings;
import uz.tabriko.repository.*;

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
    @Mock CreatorProfileRepository creatorProfileRepo;
    @Mock CategoryRepository categoryRepo;
    @Mock OrderRepository orderRepo;
    @Mock PortfolioItemRepository portfolioRepo;
    @Mock ReportRepository reportRepo;
    @Mock PlatformSettingsRepository settingsRepo;
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
    void getSettings_createsDefaultWhenAbsent() {
        PlatformSettingsEntity defaults = new PlatformSettingsEntity();
        when(settingsRepo.findById(1)).thenReturn(Optional.empty());
        when(settingsRepo.save(any())).thenReturn(defaults);

        PlatformSettings result = adminService.getSettings();

        assertThat(result).isNotNull();
        verify(settingsRepo).save(any(PlatformSettingsEntity.class));
    }
}
