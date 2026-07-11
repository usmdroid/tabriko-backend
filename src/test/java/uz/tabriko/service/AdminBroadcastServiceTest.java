package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.domain.entity.BroadcastNotification;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.UserDevice;
import uz.tabriko.domain.enums.BroadcastTargetType;
import uz.tabriko.domain.enums.NotificationType;
import uz.tabriko.domain.enums.Platform;
import uz.tabriko.dto.request.BroadcastNotificationRequest;
import uz.tabriko.dto.request.BroadcastTarget;
import uz.tabriko.dto.response.BroadcastResponse;
import uz.tabriko.repository.BroadcastNotificationRepository;
import uz.tabriko.repository.UserDeviceRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBroadcastServiceTest {

    @Mock UserDeviceRepository userDeviceRepo;
    @Mock NotificationService notificationService;
    @Mock BroadcastNotificationRepository broadcastRepo;

    @InjectMocks AdminBroadcastService service;

    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
    }

    // --- helpers ---

    private BroadcastNotificationRequest reqWithType(BroadcastTargetType type) {
        BroadcastTarget target = new BroadcastTarget();
        target.setType(type);
        BroadcastNotificationRequest req = new BroadcastNotificationRequest();
        req.setTitle("Hello");
        req.setBody("World");
        req.setTarget(target);
        return req;
    }

    private UserDevice deviceFor(UUID userId, String version, Platform platform) {
        User user = new User();
        user.setId(userId);
        UserDevice d = new UserDevice();
        d.setUser(user);
        d.setAppVersion(version);
        d.setPlatform(platform);
        d.setFcmToken("token-" + UUID.randomUUID());
        return d;
    }

    // --- ALL ---

    @Test
    void broadcast_all_targetsEveryDistinctDeviceOwner() {
        when(userDeviceRepo.findDistinctUserIds()).thenReturn(List.of(userA, userB));
        when(userDeviceRepo.countByUserIdIn(List.of(userA, userB))).thenReturn(3L);
        when(broadcastRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        BroadcastResponse resp = service.broadcast(reqWithType(BroadcastTargetType.ALL));

        assertThat(resp.getUsers()).isEqualTo(2);
        assertThat(resp.getDevices()).isEqualTo(3);
        verify(notificationService).sendNotification(userA, "Hello", "World", NotificationType.SYSTEM);
        verify(notificationService).sendNotification(userB, "Hello", "World", NotificationType.SYSTEM);
        verify(notificationService, times(2)).sendNotification(any(), any(), any(), any());
    }

    // --- PLATFORM ---

    @Test
    void broadcast_platform_android_filtersCorrectly() {
        when(userDeviceRepo.findDistinctUserIdsByPlatform(Platform.ANDROID)).thenReturn(List.of(userA));
        when(userDeviceRepo.countByUserIdIn(List.of(userA))).thenReturn(2L);
        when(broadcastRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        BroadcastTarget t = new BroadcastTarget();
        t.setType(BroadcastTargetType.PLATFORM);
        t.setPlatform(Platform.ANDROID);
        BroadcastNotificationRequest req = new BroadcastNotificationRequest();
        req.setTitle("T");
        req.setBody("B");
        req.setTarget(t);

        BroadcastResponse resp = service.broadcast(req);

        assertThat(resp.getUsers()).isEqualTo(1);
        assertThat(resp.getDevices()).isEqualTo(2);
        verify(notificationService).sendNotification(userA, "T", "B", NotificationType.SYSTEM);
        verify(userDeviceRepo).findDistinctUserIdsByPlatform(Platform.ANDROID);
    }

    // --- VERSION ---

    @Test
    void broadcast_version_filtersDevicesByRange() {
        UserDevice dA = deviceFor(userA, "1.0.9", Platform.ANDROID);
        UserDevice dB = deviceFor(userB, "1.0.20", Platform.IOS);
        when(userDeviceRepo.findAllWithAppVersion()).thenReturn(List.of(dA, dB));
        when(userDeviceRepo.countByUserIdIn(any())).thenReturn(1L);
        when(broadcastRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        BroadcastTarget t = new BroadcastTarget();
        t.setType(BroadcastTargetType.VERSION);
        t.setMaxVersion("1.0.18"); // versions < 1.0.18 — only dA qualifies
        BroadcastNotificationRequest req = new BroadcastNotificationRequest();
        req.setTitle("T");
        req.setBody("B");
        req.setTarget(t);

        BroadcastResponse resp = service.broadcast(req);

        assertThat(resp.getUsers()).isEqualTo(1);
        verify(notificationService).sendNotification(userA, "T", "B", NotificationType.SYSTEM);
        verify(notificationService, never()).sendNotification(eq(userB), any(), any(), any());
    }

    @Test
    void broadcast_version_multiDeviceUser_countedOnceAsSingleUser() {
        UserDevice d1 = deviceFor(userA, "1.0.9", Platform.ANDROID);
        UserDevice d2 = deviceFor(userA, "1.0.9", Platform.IOS);
        when(userDeviceRepo.findAllWithAppVersion()).thenReturn(List.of(d1, d2));
        when(userDeviceRepo.countByUserIdIn(any())).thenReturn(2L);
        when(broadcastRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        BroadcastTarget t = new BroadcastTarget();
        t.setType(BroadcastTargetType.VERSION);
        t.setMaxVersion("1.0.18");
        BroadcastNotificationRequest req = new BroadcastNotificationRequest();
        req.setTitle("T");
        req.setBody("B");
        req.setTarget(t);

        BroadcastResponse resp = service.broadcast(req);

        // userA appears twice in devices but sendNotification is called once
        assertThat(resp.getUsers()).isEqualTo(1);
        verify(notificationService, times(1)).sendNotification(eq(userA), any(), any(), any());
    }

    // --- EMPTY TARGET ---

    @Test
    void broadcast_emptyTarget_returnsZerosWithoutError() {
        when(userDeviceRepo.findDistinctUserIds()).thenReturn(List.of());
        when(userDeviceRepo.countByUserIdIn(List.of())).thenReturn(0L);
        when(broadcastRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatCode(() -> {
            BroadcastResponse resp = service.broadcast(reqWithType(BroadcastTargetType.ALL));
            assertThat(resp.getUsers()).isEqualTo(0);
            assertThat(resp.getDevices()).isEqualTo(0);
        }).doesNotThrowAnyException();

        verify(notificationService, never()).sendNotification(any(), any(), any(), any());
    }

    // --- HISTORY ---

    @Test
    void broadcast_savesHistoryRow() {
        when(userDeviceRepo.findDistinctUserIds()).thenReturn(List.of(userA));
        when(userDeviceRepo.countByUserIdIn(any())).thenReturn(1L);
        when(broadcastRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.broadcast(reqWithType(BroadcastTargetType.ALL));

        verify(broadcastRepo).save(any(BroadcastNotification.class));
    }
}
