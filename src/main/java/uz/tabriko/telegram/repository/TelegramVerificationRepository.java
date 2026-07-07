package uz.tabriko.telegram.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.tabriko.telegram.entity.TelegramVerification;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelegramVerificationRepository extends JpaRepository<TelegramVerification, UUID> {
    Optional<TelegramVerification> findFirstByTelegramUserIdOrderByCreatedAtDesc(Long telegramUserId);

    Optional<TelegramVerification> findFirstByPhoneOrderByCreatedAtDesc(String phone);
}
