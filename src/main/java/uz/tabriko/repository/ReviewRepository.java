package uz.tabriko.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.Review;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByCreatorIdOrderByCreatedAtDesc(UUID creatorId, Pageable pageable);

    Optional<Review> findByOrderId(UUID orderId);

    @Query("SELECT AVG(r.stars) FROM Review r WHERE r.creator.id = :creatorId")
    Double calculateAvgRating(UUID creatorId);

    long countByCreatorId(UUID creatorId);
}
