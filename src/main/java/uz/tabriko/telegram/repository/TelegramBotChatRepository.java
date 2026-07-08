package uz.tabriko.telegram.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.tabriko.telegram.entity.TelegramBotChat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelegramBotChatRepository extends JpaRepository<TelegramBotChat, UUID> {
    Optional<TelegramBotChat> findByChatId(Long chatId);

    List<TelegramBotChat> findByTelegramUserIdOrderByCreatedAtDesc(Long telegramUserId);

    void deleteByChatId(Long chatId);
}
