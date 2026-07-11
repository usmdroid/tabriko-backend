package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.tabriko.domain.entity.BroadcastNotification;

import java.util.UUID;

public interface BroadcastNotificationRepository extends JpaRepository<BroadcastNotification, UUID> {
}
