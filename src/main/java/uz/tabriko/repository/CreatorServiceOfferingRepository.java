package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.CreatorServiceOffering;
import uz.tabriko.domain.enums.OrderType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorServiceOfferingRepository extends JpaRepository<CreatorServiceOffering, Long> {

    Optional<CreatorServiceOffering> findByCreator_IdAndType(UUID creatorId, OrderType type);

    List<CreatorServiceOffering> findByCreator_Id(UUID creatorId);

    List<CreatorServiceOffering> findByCreator_IdIn(List<UUID> creatorIds);
}
