package uz.tabriko.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.CreatorApplication;
import uz.tabriko.domain.enums.ApplicationStatus;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorApplicationRepository extends JpaRepository<CreatorApplication, UUID> {

    Optional<CreatorApplication> findByIdAndTrackingToken(UUID id, String trackingToken);

    Page<CreatorApplication> findByStatusOrderByCreatedAtDesc(ApplicationStatus status, Pageable pageable);

    Page<CreatorApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByPhoneAndStatusIn(String phone, Collection<ApplicationStatus> statuses);
}
