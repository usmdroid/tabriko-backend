package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.PortfolioItem;

import java.util.List;
import java.util.UUID;

@Repository
public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Long> {

    List<PortfolioItem> findByCreatorId(UUID creatorId);

    // Public items: either not linked to an order, or linked to an order with client consent
    @Query("SELECT p FROM PortfolioItem p WHERE p.creator.id = :creatorId AND p.isPublic = true " +
           "AND (p.order IS NULL OR p.order.portfolioConsent = true)")
    List<PortfolioItem> findPublicWithConsent(@Param("creatorId") UUID creatorId);
}
