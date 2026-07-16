package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.CreatorViolation;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreatorViolationRepository extends JpaRepository<CreatorViolation, Long> {

    boolean existsByOrderId(UUID orderId);

    List<CreatorViolation> findAllByCreatorId(UUID creatorId);
}
