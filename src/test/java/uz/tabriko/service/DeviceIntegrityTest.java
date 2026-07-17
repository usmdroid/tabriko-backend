package uz.tabriko.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.DeviceAttestNonce;
import uz.tabriko.domain.entity.PlatformSettingsEntity;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.UserDevice;
import uz.tabriko.domain.enums.Platform;
import uz.tabriko.repository.DeviceAttestNonceRepository;
import uz.tabriko.repository.PlatformSettingsRepository;
import uz.tabriko.repository.UserDeviceRepository;
import uz.tabriko.repository.UserRepository;
import uz.tabriko.security.DeviceIntegrityFilter;
import uz.tabriko.security.UserPrincipal;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceIntegrityTest {

    // ---- UserService tests ----

    @Mock UserRepository userRepo;
    @Mock UserDeviceRepository userDeviceRepo;
    @Mock PlatformSettingsRepository settingsRepo;
    @Mock UserMapper userMapper;

    @InjectMocks UserService userService;

    // ---- AdminService mocks (separate @InjectMocks instance requires field-level setup) ----

    @Mock uz.tabriko.repository.CreatorProfileRepository creatorProfileRepo;
    @Mock uz.tabriko.repository.CategoryRepository categoryRepo;
    @Mock uz.tabriko.repository.OrderRepository orderRepo;
    @Mock uz.tabriko.repository.PortfolioItemRepository portfolioRepo;
    @Mock uz.tabriko.repository.ReportRepository reportRepo;
    @Mock uz.tabriko.repository.WalletTransactionRepository walletTxRepo;
    @Mock uz.tabriko.repository.CreatorContactRepository contactRepo;
    @Mock uz.tabriko.infrastructure.payment.PaymentGateway paymentGateway;
    @Mock NotificationService notificationService;
    @Mock uz.tabriko.infrastructure.firebase.PushNotificationService pushService;
    @Mock UserMapper adminMapper;

    @InjectMocks AdminService adminService;

    // ---- DeviceAttestService tests ----

    @Mock DeviceAttestNonceRepository nonceRepo;
    @Mock IntegrityVerifier integrityVerifier;

    @InjectMocks DeviceAttestService deviceAttestService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userDeviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userDeviceRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // Scenario 1: New device registered with deviceId + rooted=false → saved, rooted=false, blocked=false

    @Test
    void registerFcmToken_newDevice_savedWithRootedFalse() {
        when(userDeviceRepo.findByUserIdAndDeviceId(userId, "dev-new")).thenReturn(Optional.empty());
        PlatformSettingsEntity settings = new PlatformSettingsEntity();
        settings.setBlockRootedDevices(false);
        when(settingsRepo.findById(1)).thenReturn(Optional.of(settings));

        userService.registerFcmToken(userId, "tok", Platform.ANDROID, "1.0", null, null, "dev-new", false);

        ArgumentCaptor<UserDevice> cap = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepo).save(cap.capture());
        assertThat(cap.getValue().isRooted()).isFalse();
        assertThat(cap.getValue().isBlocked()).isFalse();
    }

    // Scenario 2: Same (userId, deviceId) → upserts existing record, no duplicate

    @Test
    void registerFcmToken_sameDeviceId_upserts() {
        UserDevice existing = new UserDevice();
        existing.setDeviceId("dev-upsert");
        existing.setFcmToken("old-tok");
        when(userDeviceRepo.findByUserIdAndDeviceId(userId, "dev-upsert")).thenReturn(Optional.of(existing));
        PlatformSettingsEntity settings = new PlatformSettingsEntity();
        settings.setBlockRootedDevices(false);
        when(settingsRepo.findById(1)).thenReturn(Optional.of(settings));

        userService.registerFcmToken(userId, "new-tok", Platform.ANDROID, "1.0", null, null, "dev-upsert", false);

        ArgumentCaptor<UserDevice> cap = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepo, times(1)).save(cap.capture());
        assertThat(cap.getValue()).isSameAs(existing);
        assertThat(cap.getValue().getFcmToken()).isEqualTo("new-tok");
    }

    // Scenario 3: Account switch — token T belongs to userA's row; userB logs in on same
    // device (different deviceId) → stale row for userA deleted, userB's row saved with token T.

    @Test
    void registerFcmToken_tokenOwnedByOtherUser_differentDeviceId_claimsToken() {
        UUID userAId = UUID.randomUUID();
        User userA = new User();
        userA.setId(userAId);

        UserDevice staleDevice = new UserDevice();
        staleDevice.setId(UUID.randomUUID());
        staleDevice.setUser(userA);
        staleDevice.setDeviceId("dev-A");
        staleDevice.setFcmToken("shared-token");

        // userB has no row for "dev-B" yet
        when(userDeviceRepo.findByUserIdAndDeviceId(userId, "dev-B")).thenReturn(Optional.empty());
        // but "shared-token" is already held by userA's row
        when(userDeviceRepo.findByFcmToken("shared-token")).thenReturn(Optional.of(staleDevice));
        PlatformSettingsEntity settings = new PlatformSettingsEntity();
        settings.setBlockRootedDevices(false);
        when(settingsRepo.findById(1)).thenReturn(Optional.of(settings));

        userService.registerFcmToken(userId, "shared-token", Platform.ANDROID, "1.0", null, null, "dev-B", false);

        verify(userDeviceRepo).delete(staleDevice);
        verify(userDeviceRepo).flush();
        ArgumentCaptor<UserDevice> cap = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepo).save(cap.capture());
        assertThat(cap.getValue().getUser()).isEqualTo(user);
        assertThat(cap.getValue().getFcmToken()).isEqualTo("shared-token");
    }

    // Scenario 4: Account switch — same deviceId, new user. Row (userA, devD, tokenT) exists;
    // userB registers with (devD, tokenT). Stale row deleted, userB's new row saved.

    @Test
    void registerFcmToken_tokenOwnedByOtherUser_sameDeviceId_claimsToken() {
        UUID userAId = UUID.randomUUID();
        User userA = new User();
        userA.setId(userAId);

        UserDevice staleDevice = new UserDevice();
        staleDevice.setId(UUID.randomUUID());
        staleDevice.setUser(userA);
        staleDevice.setDeviceId("dev-D");
        staleDevice.setFcmToken("token-T");

        // userB's lookup by (userB, dev-D) returns nothing
        when(userDeviceRepo.findByUserIdAndDeviceId(userId, "dev-D")).thenReturn(Optional.empty());
        when(userDeviceRepo.findByFcmToken("token-T")).thenReturn(Optional.of(staleDevice));
        PlatformSettingsEntity settings = new PlatformSettingsEntity();
        settings.setBlockRootedDevices(false);
        when(settingsRepo.findById(1)).thenReturn(Optional.of(settings));

        userService.registerFcmToken(userId, "token-T", Platform.ANDROID, "1.0", null, null, "dev-D", false);

        verify(userDeviceRepo).delete(staleDevice);
        verify(userDeviceRepo).flush();
        ArgumentCaptor<UserDevice> cap = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepo).save(cap.capture());
        assertThat(cap.getValue().getUser()).isEqualTo(user);
        assertThat(cap.getValue().getFcmToken()).isEqualTo("token-T");
        // only one row should exist after this call (the stale was deleted)
    }

    // Scenario 5: Idempotent re-registration — same user, same deviceId, same token.
    // The resolved row IS the token row (same id); no delete, just an upsert.

    @Test
    void registerFcmToken_sameUserSameDeviceIdSameToken_idempotent() {
        UUID rowId = UUID.randomUUID();
        UserDevice existing = new UserDevice();
        existing.setId(rowId);
        existing.setUser(user);
        existing.setDeviceId("dev-X");
        existing.setFcmToken("token-X");

        when(userDeviceRepo.findByUserIdAndDeviceId(userId, "dev-X")).thenReturn(Optional.of(existing));
        // findByFcmToken returns the SAME row (same id)
        when(userDeviceRepo.findByFcmToken("token-X")).thenReturn(Optional.of(existing));
        PlatformSettingsEntity settings = new PlatformSettingsEntity();
        settings.setBlockRootedDevices(false);
        when(settingsRepo.findById(1)).thenReturn(Optional.of(settings));

        userService.registerFcmToken(userId, "token-X", Platform.ANDROID, "2.0", null, null, "dev-X", false);

        verify(userDeviceRepo, never()).delete(any(UserDevice.class));
        verify(userDeviceRepo, never()).flush();
        verify(userDeviceRepo).save(existing);
    }

    // Scenario 6: No deviceId (old client) → falls back to fcm_token upsert

    @Test
    void registerFcmToken_noDeviceId_fallsBackToFcmTokenLookup() {
        UserDevice existing = new UserDevice();
        existing.setFcmToken("existing-tok");
        when(userDeviceRepo.findByFcmToken("existing-tok")).thenReturn(Optional.of(existing));
        PlatformSettingsEntity settings = new PlatformSettingsEntity();
        settings.setBlockRootedDevices(false);
        when(settingsRepo.findById(1)).thenReturn(Optional.of(settings));

        userService.registerFcmToken(userId, "existing-tok", Platform.ANDROID, "1.0", null, null, null, false);

        verify(userDeviceRepo, never()).findByUserIdAndDeviceId(any(), any());
        verify(userDeviceRepo).findByFcmToken("existing-tok");
        verify(userDeviceRepo).save(any());
    }

    // 1. Blocked device → 403 on registerFcmToken

    @Test
    void registerFcmToken_blockedDevice_throws403() {
        UserDevice blocked = new UserDevice();
        blocked.setBlocked(true);
        blocked.setDeviceId("dev-1");
        when(userDeviceRepo.findByUserIdAndDeviceId(userId, "dev-1")).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() ->
                userService.registerFcmToken(userId, "tok", Platform.ANDROID, "1.0", null, null, "dev-1", false))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    // 2. Rooted device + blockRootedDevices=true → 403

    @Test
    void registerFcmToken_rootedDeviceWithPolicyEnabled_throws403() {
        when(userDeviceRepo.findByUserIdAndDeviceId(userId, "dev-2")).thenReturn(Optional.empty());

        PlatformSettingsEntity settings = new PlatformSettingsEntity();
        settings.setBlockRootedDevices(true);
        when(settingsRepo.findById(1)).thenReturn(Optional.of(settings));

        assertThatThrownBy(() ->
                userService.registerFcmToken(userId, "tok", Platform.ANDROID, "1.0", null, null, "dev-2", true))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    // 3. Rooted device + blockRootedDevices=false → 200 (no exception)

    @Test
    void registerFcmToken_rootedDeviceWithPolicyDisabled_allowed() {
        when(userDeviceRepo.findByUserIdAndDeviceId(userId, "dev-3")).thenReturn(Optional.empty());

        PlatformSettingsEntity settings = new PlatformSettingsEntity();
        settings.setBlockRootedDevices(false);
        when(settingsRepo.findById(1)).thenReturn(Optional.of(settings));

        userService.registerFcmToken(userId, "tok", Platform.ANDROID, "1.0", null, null, "dev-3", true);

        verify(userDeviceRepo).save(any());
    }

    // 4. Admin block/unblock endpoints change blocked flag

    @Test
    void blockDevice_setsBlockedTrue() {
        UserDevice device = new UserDevice();
        device.setDeviceId("dev-x");
        device.setBlocked(false);
        when(userDeviceRepo.findAllByDeviceId("dev-x")).thenReturn(List.of(device));

        adminService.blockDevice("dev-x");

        assertThat(device.isBlocked()).isTrue();
        verify(userDeviceRepo).saveAll(List.of(device));
    }

    @Test
    void unblockDevice_setsBlockedFalse() {
        UserDevice device = new UserDevice();
        device.setDeviceId("dev-y");
        device.setBlocked(true);
        when(userDeviceRepo.findAllByDeviceId("dev-y")).thenReturn(List.of(device));

        adminService.unblockDevice("dev-y");

        assertThat(device.isBlocked()).isFalse();
        verify(userDeviceRepo).saveAll(List.of(device));
    }

    @Test
    void blockDevice_multipleUsersSameDevice_allBlocked() {
        UserDevice d1 = new UserDevice();
        d1.setDeviceId("shared-dev");
        d1.setBlocked(false);
        UserDevice d2 = new UserDevice();
        d2.setDeviceId("shared-dev");
        d2.setBlocked(false);
        when(userDeviceRepo.findAllByDeviceId("shared-dev")).thenReturn(List.of(d1, d2));

        adminService.blockDevice("shared-dev");

        assertThat(d1.isBlocked()).isTrue();
        assertThat(d2.isBlocked()).isTrue();
        verify(userDeviceRepo).saveAll(List.of(d1, d2));
    }

    // 5. Nonce endpoint generates unique nonce; expired nonce rejected on attest

    @Test
    void generateNonce_savesNonceWithDeviceId() {
        when(nonceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String nonce = deviceAttestService.generateNonce("dev-n");

        ArgumentCaptor<DeviceAttestNonce> cap = ArgumentCaptor.forClass(DeviceAttestNonce.class);
        verify(nonceRepo).save(cap.capture());
        assertThat(cap.getValue().getDeviceId()).isEqualTo("dev-n");
        assertThat(cap.getValue().getNonce()).isEqualTo(nonce);
        assertThat(cap.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void attest_expiredNonce_throws400() {
        doNothing().when(nonceRepo).deleteByExpiresAtBefore(any());
        when(nonceRepo.markUsed(eq("stale-nonce"), eq("dev-n"))).thenReturn(0);

        assertThatThrownBy(() ->
                deviceAttestService.attest(userId, "dev-n", Platform.ANDROID, "token", "stale-nonce"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void attest_usedNonceReplay_throws400() {
        doNothing().when(nonceRepo).deleteByExpiresAtBefore(any());
        when(nonceRepo.markUsed(eq("used-nonce"), eq("dev-n"))).thenReturn(0);

        assertThatThrownBy(() ->
                deviceAttestService.attest(userId, "dev-n", Platform.ANDROID, "token", "used-nonce"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    // 6. Attest with mock verifier returning genuine=true → device updated

    @Test
    void attest_mockVerifierReturnsTrue_updatesDeviceGenuine() {
        doNothing().when(nonceRepo).deleteByExpiresAtBefore(any());
        when(nonceRepo.markUsed("valid-nonce", "dev-a")).thenReturn(1);
        when(integrityVerifier.verify(Platform.ANDROID, "dev-a", "integrity-token")).thenReturn(true);

        UserDevice device = new UserDevice();
        when(userDeviceRepo.findByUserIdAndDeviceId(userId, "dev-a")).thenReturn(Optional.of(device));

        boolean result = deviceAttestService.attest(userId, "dev-a", Platform.ANDROID, "integrity-token", "valid-nonce");

        assertThat(result).isTrue();
        ArgumentCaptor<UserDevice> cap = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepo).save(cap.capture());
        assertThat(cap.getValue().getGenuine()).isTrue();
        assertThat(cap.getValue().isRooted()).isFalse();
    }

    // Scenario 8: NoOpIntegrityVerifier returns false → genuine=false, rooted=true

    @Test
    void attest_noOpVerifierReturnsFalse_setsGenuineFalseAndRootedTrue() {
        doNothing().when(nonceRepo).deleteByExpiresAtBefore(any());
        when(nonceRepo.markUsed("noop-nonce", "dev-b")).thenReturn(1);
        when(integrityVerifier.verify(Platform.ANDROID, "dev-b", "bad-token")).thenReturn(false);
        UserDevice device = new UserDevice();
        when(userDeviceRepo.findByUserIdAndDeviceId(userId, "dev-b")).thenReturn(Optional.of(device));

        boolean result = deviceAttestService.attest(userId, "dev-b", Platform.ANDROID, "bad-token", "noop-nonce");

        assertThat(result).isFalse();
        ArgumentCaptor<UserDevice> cap = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepo).save(cap.capture());
        assertThat(cap.getValue().getGenuine()).isFalse();
        assertThat(cap.getValue().isRooted()).isTrue();
    }

    // Scenario 11: Authenticated request with X-Device-Id for blocked device → 403 DEVICE_BLOCKED

    @Test
    void deviceIntegrityFilter_blockedDevice_returns403() throws Exception {
        UUID filterUserId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(filterUserId, "+998901234567", "CLIENT");
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserDevice blocked = new UserDevice();
        blocked.setBlocked(true);
        when(userDeviceRepo.findByUserIdAndDeviceId(filterUserId, "dev-blocked")).thenReturn(Optional.of(blocked));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        PrintWriter writer = mock(PrintWriter.class);
        when(request.getHeader("X-Device-Id")).thenReturn("dev-blocked");
        when(response.getWriter()).thenReturn(writer);

        DeviceIntegrityFilter filter = new DeviceIntegrityFilter(userDeviceRepo, settingsRepo);
        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(writer).write(contains("DEVICE_BLOCKED"));
        verify(chain, never()).doFilter(any(), any());
    }

    // Scenario 12: Authenticated request with X-Device-Id for unknown device → passes through (200)

    @Test
    void deviceIntegrityFilter_unknownDevice_passesThrough() throws Exception {
        UUID filterUserId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(filterUserId, "+998900000001", "CLIENT");
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userDeviceRepo.findByUserIdAndDeviceId(filterUserId, "dev-unknown")).thenReturn(Optional.empty());

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("X-Device-Id")).thenReturn("dev-unknown");

        DeviceIntegrityFilter filter = new DeviceIntegrityFilter(userDeviceRepo, settingsRepo);
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    // Scenario 13: Authenticated request without X-Device-Id header → passes through (200)

    @Test
    void deviceIntegrityFilter_noDeviceIdHeader_passesThrough() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("X-Device-Id")).thenReturn(null);

        DeviceIntegrityFilter filter = new DeviceIntegrityFilter(userDeviceRepo, settingsRepo);
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    // 7. DeviceIntegrityFilter: genuine=false + blockRootedDevices=true → 403

    @Test
    void deviceIntegrityFilter_genuineFalseBlockRootedEnabled_returns403() throws Exception {
        UUID filterUserId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(filterUserId, "+998901234567", "CLIENT");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserDevice device = new UserDevice();
        device.setGenuine(false);
        device.setRooted(false);
        device.setBlocked(false);

        PlatformSettingsEntity settings = new PlatformSettingsEntity();
        settings.setBlockRootedDevices(true);

        when(userDeviceRepo.findByUserIdAndDeviceId(filterUserId, "dev-z")).thenReturn(Optional.of(device));
        when(settingsRepo.findById(1)).thenReturn(Optional.of(settings));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        PrintWriter writer = mock(PrintWriter.class);

        when(request.getHeader("X-Device-Id")).thenReturn("dev-z");
        when(response.getWriter()).thenReturn(writer);

        DeviceIntegrityFilter filter = new DeviceIntegrityFilter(userDeviceRepo, settingsRepo);
        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }
}
