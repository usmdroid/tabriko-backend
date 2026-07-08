package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.Promotion;

import java.util.List;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<Promotion> findAllByOrderBySortOrderAscIdAsc();
}
