package uz.tabriko.telegram.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.tabriko.telegram.entity.TelegramAlertSubscriber;

public interface TelegramAlertSubscriberRepository
        extends JpaRepository<TelegramAlertSubscriber, Long> {
}
