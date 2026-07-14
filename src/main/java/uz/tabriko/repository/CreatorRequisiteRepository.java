package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.tabriko.domain.entity.CreatorRequisite;
import uz.tabriko.domain.enums.OrderType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreatorRequisiteRepository extends JpaRepository<CreatorRequisite, Long> {

    List<CreatorRequisite> findByCreatorUserIdOrderByCreatedAtAsc(UUID creatorUserId);

    List<CreatorRequisite> findByCreatorUserIdAndServiceTypeOrderByCreatedAtAsc(UUID creatorUserId, OrderType serviceType);

    long countByCreatorUserIdAndServiceType(UUID creatorUserId, OrderType serviceType);

    boolean existsByCreatorUserIdAndServiceTypeAndNameIgnoreCase(UUID creatorUserId, OrderType serviceType, String name);

    Optional<CreatorRequisite> findByIdAndCreatorUserId(Long id, UUID creatorUserId);
}
