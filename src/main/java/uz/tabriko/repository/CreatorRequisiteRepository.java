package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.tabriko.domain.entity.CreatorRequisite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreatorRequisiteRepository extends JpaRepository<CreatorRequisite, Long> {
    List<CreatorRequisite> findByCreatorUserIdOrderByCreatedAtAsc(UUID creatorUserId);
    long countByCreatorUserId(UUID creatorUserId);
    boolean existsByCreatorUserIdAndNameIgnoreCase(UUID creatorUserId, String name);
    Optional<CreatorRequisite> findByIdAndCreatorUserId(Long id, UUID creatorUserId);
}
