package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.tabriko.domain.entity.UserDevice;
import uz.tabriko.domain.enums.Platform;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {
    Optional<UserDevice> findByFcmToken(String fcmToken);
    List<UserDevice> findByUserId(UUID userId);
    void deleteByFcmToken(String fcmToken);
    Optional<UserDevice> findByUserIdAndDeviceId(UUID userId, String deviceId);
    Optional<UserDevice> findByDeviceId(String deviceId);
    List<UserDevice> findAllByDeviceId(String deviceId);

    @Query("SELECT DISTINCT ud.user.id FROM UserDevice ud")
    List<UUID> findDistinctUserIds();

    @Query("SELECT DISTINCT ud.user.id FROM UserDevice ud WHERE ud.platform = :platform")
    List<UUID> findDistinctUserIdsByPlatform(@Param("platform") Platform platform);

    @Query("SELECT ud FROM UserDevice ud JOIN FETCH ud.user WHERE ud.appVersion IS NOT NULL")
    List<UserDevice> findAllWithAppVersion();

    long countByUserIdIn(Collection<UUID> userIds);
}
