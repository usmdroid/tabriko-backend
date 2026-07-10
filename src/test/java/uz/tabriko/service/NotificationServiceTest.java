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
import uz.tabriko.domain.enums.NotificationType;
import uz.tabriko.infrastructure.firebase.PushNotificationService;
import uz.tabriko.repository.NotificationRepository;
import uz.tabriko.repository.UserRepository;

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
    void sendNotification_userHasFcmToken_savesAndSendsPush() {
        user.setFcmToken("device-token-1");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        notificationService.sendNotification(userId, "Title", "Body", NotificationType.ORDER_RECEIVED);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Title");
        assertThat(captor.getValue().getBody()).isEqualTo("Body");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.ORDER_RECEIVED);
        assertThat(captor.getValue().getUser()).isEqualTo(user);

        verify(pushService).sendPush("device-token-1", "Title", "Body");
    }

    @Test
    void sendNotification_userHasNoFcmToken_savesButSkipsPush() {
        user.setFcmToken(null);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        notificationService.sendNotification(userId, "Title", "Body", NotificationType.ORDER_DELIVERED);

        verify(notificationRepo).save(any());
        verify(pushService, never()).sendPush(any(), any(), any());
    }

    @Test
    void sendNotification_userNotFound_silentlyNoOps() {
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        notificationService.sendNotification(userId, "Title", "Body", NotificationType.ORDER_ACCEPTED);

        verify(notificationRepo, never()).save(any());
        verify(pushService, never()).sendPush(any(), any(), any());
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
