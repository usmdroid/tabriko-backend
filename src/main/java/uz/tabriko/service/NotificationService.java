package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.Notification;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.UserDevice;
import uz.tabriko.domain.enums.NotificationType;
import uz.tabriko.dto.response.NotificationResponse;
import uz.tabriko.dto.response.PageResponse;
import uz.tabriko.infrastructure.firebase.PushNotificationService;
import uz.tabriko.repository.NotificationRepository;
import uz.tabriko.repository.UserDeviceRepository;
import uz.tabriko.repository.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final UserRepository userRepo;
    private final UserDeviceRepository userDeviceRepo;
    private final PushNotificationService pushService;
    private final UserMapper mapper;

    public PageResponse<NotificationResponse> getNotifications(UUID userId, int page, int size) {
        return PageResponse.of(
                notificationRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)),
                mapper::toNotificationResponse
        );
    }

    @Transactional
    public void markRead(UUID userId, Long notificationId) {
        Notification n = notificationRepo.findById(notificationId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        if (!n.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Not your notification");
        }
        n.setRead(true);
        notificationRepo.save(n);
    }

    @Transactional
    public void createInAppNotification(UUID userId, String title, String body, NotificationType type) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return;

        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setBody(body);
        n.setType(type);
        notificationRepo.save(n);
    }

    @Transactional
    public void sendNotification(UUID userId, String title, String body, NotificationType type) {
        sendNotification(userId, title, body, type, null);
    }

    @Transactional
    public void sendNotification(UUID userId, String title, String body, NotificationType type, UUID orderId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return;

        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setBody(body);
        n.setType(type);
        notificationRepo.save(n);

        Map<String, String> data = new HashMap<>();
        data.put("type", type.name());
        if (orderId != null) {
            data.put("orderId", orderId.toString());
        }

        List<UserDevice> devices = userDeviceRepo.findByUserId(userId);
        for (UserDevice device : devices) {
            try {
                pushService.sendPush(device.getFcmToken(), title, body, data);
            } catch (PushNotificationService.DeadTokenException e) {
                userDeviceRepo.deleteByFcmToken(e.getMessage());
            }
        }
    }
}
