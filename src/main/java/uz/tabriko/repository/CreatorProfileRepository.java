package uz.tabriko.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.Category;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.enums.CreatorTier;
import uz.tabriko.domain.enums.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, UUID> {

    @Query(value = """
            SELECT cp FROM CreatorProfile cp
            JOIN FETCH cp.user u
            LEFT JOIN FETCH cp.category
            WHERE (:categoryId IS NULL OR cp.category.id = :categoryId)
              AND (:search IS NULL OR LOWER(u.name) LIKE :searchPattern)
              AND cp.isVerified = true
              AND cp.profileComplete = true
              AND cp.user.status = uz.tabriko.domain.enums.UserStatus.ACTIVE
            """,
            countQuery = """
            SELECT COUNT(cp) FROM CreatorProfile cp
            JOIN cp.user u
            WHERE (:categoryId IS NULL OR cp.category.id = :categoryId)
              AND (:search IS NULL OR LOWER(u.name) LIKE :searchPattern)
              AND cp.isVerified = true
              AND cp.profileComplete = true
              AND cp.user.status = uz.tabriko.domain.enums.UserStatus.ACTIVE
            """)
    Page<CreatorProfile> findAllFiltered(
            @Param("categoryId") Long categoryId,
            @Param("search") String search,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );

    @Query("SELECT cp FROM CreatorProfile cp JOIN FETCH cp.user LEFT JOIN FETCH cp.category " +
           "WHERE cp.isTop = true AND cp.isVerified = true AND cp.profileComplete = true " +
           "AND cp.user.status = uz.tabriko.domain.enums.UserStatus.ACTIVE ORDER BY cp.avgRating DESC")
    List<CreatorProfile> findTop10();

    @Query("SELECT cp FROM CreatorProfile cp JOIN FETCH cp.user LEFT JOIN FETCH cp.category " +
           "WHERE cp.isVerified = true AND cp.profileComplete = true " +
           "AND cp.user.status = uz.tabriko.domain.enums.UserStatus.ACTIVE ORDER BY cp.ratingCount DESC")
    List<CreatorProfile> findForYou(Pageable pageable);

    Optional<CreatorProfile> findByUserId(UUID userId);

    boolean existsByPublicCode(String publicCode);

    Optional<CreatorProfile> findByPublicCode(String publicCode);

    @Query("SELECT COUNT(cp) FROM CreatorProfile cp WHERE cp.isVerified = true AND cp.profileComplete = true AND cp.user.status = :activeStatus")
    long countActiveCreators(@Param("activeStatus") UserStatus activeStatus);

    @Query(value = "SELECT cp FROM CreatorProfile cp JOIN FETCH cp.user LEFT JOIN FETCH cp.category "
            + "WHERE cp.user.status <> uz.tabriko.domain.enums.UserStatus.DELETED",
            countQuery = "SELECT COUNT(cp) FROM CreatorProfile cp "
                    + "WHERE cp.user.status <> uz.tabriko.domain.enums.UserStatus.DELETED")
    Page<CreatorProfile> findAllWithUser(Pageable pageable);

    @Query(value = """
            SELECT cp FROM CreatorProfile cp
            JOIN FETCH cp.user
            LEFT JOIN FETCH cp.category
            WHERE cp.isVerified = true
              AND cp.profileComplete = true
              AND cp.user.status = uz.tabriko.domain.enums.UserStatus.ACTIVE
            ORDER BY (
              SELECT COUNT(o) FROM Order o
              WHERE o.creator = cp.user
                AND o.createdAt >= :cutoff
            ) DESC, cp.avgRating DESC
            """,
            countQuery = """
            SELECT COUNT(cp) FROM CreatorProfile cp
            WHERE cp.isVerified = true AND cp.profileComplete = true
              AND cp.user.status = uz.tabriko.domain.enums.UserStatus.ACTIVE
            """)
    Page<CreatorProfile> findTrending(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Query("""
            SELECT cp FROM CreatorProfile cp
            JOIN FETCH cp.user
            LEFT JOIN FETCH cp.category
            WHERE cp.id <> :excludeId
              AND cp.isVerified = true
              AND cp.profileComplete = true
              AND cp.user.status = uz.tabriko.domain.enums.UserStatus.ACTIVE
              AND (cp.category = :category OR cp.tier = :tier)
            ORDER BY
              CASE WHEN cp.category = :category THEN 0 ELSE 1 END ASC,
              cp.avgRating DESC
            """)
    List<CreatorProfile> findSimilar(
            @Param("excludeId") UUID excludeId,
            @Param("category") Category category,
            @Param("tier") CreatorTier tier,
            Pageable pageable);
}
