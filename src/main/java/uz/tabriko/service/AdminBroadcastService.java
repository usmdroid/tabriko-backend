package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.util.VersionUtil;
import uz.tabriko.domain.entity.BroadcastNotification;
import uz.tabriko.domain.enums.NotificationType;
import uz.tabriko.dto.request.BroadcastNotificationRequest;
import uz.tabriko.dto.request.BroadcastTarget;
import uz.tabriko.dto.response.BroadcastResponse;
import uz.tabriko.repository.BroadcastNotificationRepository;
import uz.tabriko.repository.UserDeviceRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminBroadcastService {

    private static final int BATCH_SIZE = 200;

    private final UserDeviceRepository userDeviceRepo;
    private final NotificationService notificationService;
    private final BroadcastNotificationRepository broadcastRepo;

    @Transactional
    public BroadcastResponse broadcast(BroadcastNotificationRequest req) {
        BroadcastTarget t = req.getTarget();
        List<UUID> targetUserIds = switch (t.getType()) {
            case ALL -> userDeviceRepo.findDistinctUserIds();
            case PLATFORM -> userDeviceRepo.findDistinctUserIdsByPlatform(t.getPlatform());
            case VERSION -> userDeviceRepo.findAllWithAppVersion().stream()
                    .filter(d -> VersionUtil.isInRange(d.getAppVersion(), t.getMinVersion(), t.getMaxVersion()))
                    .map(d -> d.getUser().getId())
                    .distinct()
                    .toList();
        };

        long deviceCount = userDeviceRepo.countByUserIdIn(targetUserIds);

        for (int i = 0; i < targetUserIds.size(); i += BATCH_SIZE) {
            List<UUID> batch = targetUserIds.subList(i, Math.min(i + BATCH_SIZE, targetUserIds.size()));
            for (UUID userId : batch) {
                notificationService.sendNotification(userId, req.getTitle(), req.getBody(), NotificationType.SYSTEM);
            }
        }

        broadcastRepo.save(BroadcastNotification.of(req, targetUserIds.size(), (int) deviceCount));

        return new BroadcastResponse(targetUserIds.size(), (int) deviceCount);
    }
}
