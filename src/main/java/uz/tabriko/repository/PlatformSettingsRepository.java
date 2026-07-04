package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.PlatformSettingsEntity;

@Repository
public interface PlatformSettingsRepository extends JpaRepository<PlatformSettingsEntity, Integer> {
}
