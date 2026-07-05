package uz.tabriko.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.enums.UserStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, UUID> {

    @Query("""
            SELECT cp FROM CreatorProfile cp
            JOIN cp.user u
            WHERE (:categoryId IS NULL OR cp.category.id = :categoryId)
              AND (:search IS NULL OR LOWER(u.name) LIKE :searchPattern)
              AND cp.isVerified = true
              AND cp.profileComplete = true
            """)
    Page<CreatorProfile> findAllFiltered(
            @Param("categoryId") Long categoryId,
            @Param("search") String search,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );

    @Query("SELECT cp FROM CreatorProfile cp WHERE cp.isTop = true AND cp.isVerified = true AND cp.profileComplete = true ORDER BY cp.avgRating DESC")
    List<CreatorProfile> findTop10();

    @Query("SELECT cp FROM CreatorProfile cp WHERE cp.isVerified = true AND cp.profileComplete = true ORDER BY cp.ratingCount DESC")
    List<CreatorProfile> findForYou(Pageable pageable);

    Optional<CreatorProfile> findByUserId(UUID userId);

    @Query("SELECT COUNT(cp) FROM CreatorProfile cp WHERE cp.isVerified = true AND cp.profileComplete = true AND cp.user.status = :activeStatus")
    long countActiveCreators(@Param("activeStatus") UserStatus activeStatus);
}
