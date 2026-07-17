package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.tabriko.domain.entity.CreatorModerationMessage;

import java.util.List;
import java.util.UUID;

public interface CreatorModerationMessageRepository extends JpaRepository<CreatorModerationMessage, Long> {

    List<CreatorModerationMessage> findByCreatorUserIdOrderByCreatedAtAsc(UUID creatorUserId);

    @Query("SELECT COUNT(m) FROM CreatorModerationMessage m " +
           "WHERE m.creatorUser.id = :userId " +
           "AND m.kind = uz.tabriko.domain.enums.ModerationMessageKind.WARNING")
    long countActiveWarnings(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE CreatorModerationMessage m SET m.readByCreator = true " +
           "WHERE m.creatorUser.id = :userId " +
           "AND m.readByCreator = false " +
           "AND m.authorRole <> uz.tabriko.domain.enums.ModerationAuthorRole.CREATOR")
    void markAdminMessagesRead(@Param("userId") UUID userId);
}
