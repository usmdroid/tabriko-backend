package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.Notification;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.UserDevice;
import uz.tabriko.domain.enums.NotificationType;
import uz.tabriko.infrastructure.firebase.PushNotificationService;
import uz.tabriko.repository.NotificationRepository;
import uz.tabriko.repository.UserDeviceRepository;
import uz.tabriko.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepo;
    @Mock UserRepository userRepo;
    @Mock UserDeviceRepository userDeviceRepo;
    @Mock PushNotificationService pushService;
    @Mock UserMapper mapper;

    @InjectMocks NotificationService notificationService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
    }

    @Test
    void sendNotification_userHasDevice_savesAndSendsPush() {
        UserDevice device = new UserDevice();
        device.setFcmToken("device-token-1");

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userDeviceRepo.findByUserId(userId)).thenReturn(List.of(device));

        notificationService.sendNotification(userId, "Title", "Body", NotificationType.ORDER_RECEIVED);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Title");
        assertThat(captor.getValue().getBody()).isEqualTo("Body");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.ORDER_RECEIVED);
        assertThat(captor.getValue().getUser()).isEqualTo(user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pushService).sendPush(eq("device-token-1"), eq("Title"), eq("Body"), dataCaptor.capture());
        assertThat(dataCaptor.getValue()).containsEntry("type", "ORDER_RECEIVED");
        assertThat(dataCaptor.getValue()).doesNotContainKey("orderId");
    }

    @Test
    void sendNotification_withOrderId_includesOrderIdInData() {
        UUID orderId = UUID.randomUUID();
        UserDevice device = new UserDevice();
        device.setFcmToken("device-token-1");

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userDeviceRepo.findByUserId(userId)).thenReturn(List.of(device));

        notificationService.sendNotification(userId, "Title", "Body", NotificationType.ORDER_DELIVERED, orderId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pushService).sendPush(eq("device-token-1"), eq("Title"), eq("Body"), dataCaptor.capture());
        assertThat(dataCaptor.getValue()).containsEntry("type", "ORDER_DELIVERED");
        assertThat(dataCaptor.getValue()).containsEntry("orderId", orderId.toString());
    }

    @Test
    void sendNotification_noDevices_savesButSkipsPush() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userDeviceRepo.findByUserId(userId)).thenReturn(List.of());

        notificationService.sendNotification(userId, "Title", "Body", NotificationType.ORDER_DELIVERED);

        verify(notificationRepo).save(any());
        verify(pushService, never()).sendPush(any(), any(), any(), any());
    }

    @Test
    void sendNotification_userNotFound_silentlyNoOps() {
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        notificationService.sendNotification(userId, "Title", "Body", NotificationType.ORDER_ACCEPTED);

        verify(notificationRepo, never()).save(any());
        verify(pushService, never()).sendPush(any(), any(), any(), any());
    }

    @Test
    void sendNotification_multipleDevices_fanOutToAll() {
        UserDevice device1 = new UserDevice();
        device1.setFcmToken("token-1");
        UserDevice device2 = new UserDevice();
        device2.setFcmToken("token-2");

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userDeviceRepo.findByUserId(userId)).thenReturn(List.of(device1, device2));

        notificationService.sendNotification(userId, "Title", "Body", NotificationType.ORDER_RECEIVED);

        verify(pushService).sendPush(eq("token-1"), eq("Title"), eq("Body"), any());
        verify(pushService).sendPush(eq("token-2"), eq("Title"), eq("Body"), any());
        verify(pushService, times(2)).sendPush(any(), any(), any(), any());
    }

    @Test
    void sendNotification_deadToken_deletesDeviceAndContinues() {
        UserDevice dead = new UserDevice();
        dead.setFcmToken("dead-token");
        UserDevice alive = new UserDevice();
        alive.setFcmToken("alive-token");

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userDeviceRepo.findByUserId(userId)).thenReturn(List.of(dead, alive));
        doThrow(new PushNotificationService.DeadTokenException("dead-token"))
                .when(pushService).sendPush(eq("dead-token"), any(), any(), any());

        notificationService.sendNotification(userId, "Title", "Body", NotificationType.ORDER_RECEIVED);

        verify(userDeviceRepo).deleteByFcmToken("dead-token");
        verify(pushService).sendPush(eq("alive-token"), any(), any(), any());
    }

    @Test
    void markRead_ownNotification_marksReadAndSaves() {
        Notification n = new Notification();
        n.setUser(user);
        n.setRead(false);
        when(notificationRepo.findById(1L)).thenReturn(Optional.of(n));

        notificationService.markRead(userId, 1L);

        assertThat(n.isRead()).isTrue();
        verify(notificationRepo).save(n);
    }

    @Test
    void markRead_notOwner_throwsForbidden() {
        Notification n = new Notification();
        n.setUser(user);
        when(notificationRepo.findById(1L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.markRead(UUID.randomUUID(), 1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Not your notification");

        verify(notificationRepo, never()).save(any());
    }

    @Test
    void markRead_notificationMissing_throwsNotFound() {
        when(notificationRepo.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(userId, 1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Notification not found");
    }
}
