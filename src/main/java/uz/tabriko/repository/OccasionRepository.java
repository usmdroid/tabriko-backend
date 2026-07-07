package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.Occasion;

import java.util.List;

@Repository
public interface OccasionRepository extends JpaRepository<Occasion, Long> {
    List<Occasion> findByActiveTrue();

    List<Occasion> findAllByOrderBySortOrderAscIdAsc();
}
