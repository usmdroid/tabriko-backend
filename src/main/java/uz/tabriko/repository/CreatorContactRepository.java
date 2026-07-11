package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.tabriko.domain.entity.CreatorContact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreatorContactRepository extends JpaRepository<CreatorContact, UUID> {

    List<CreatorContact> findByCreatorIdOrderByCreatedAtAsc(UUID creatorId);

    List<CreatorContact> findByCreatorIdIn(List<UUID> creatorIds);

    boolean existsByCreatorIdAndPhone(UUID creatorId, String phone);

    Optional<CreatorContact> findByIdAndCreatorId(UUID id, UUID creatorId);
}
