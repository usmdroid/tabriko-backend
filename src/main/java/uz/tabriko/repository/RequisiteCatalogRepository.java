package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.tabriko.domain.entity.RequisiteCatalog;

import java.util.List;

public interface RequisiteCatalogRepository extends JpaRepository<RequisiteCatalog, Long> {
    List<RequisiteCatalog> findByActiveTrue();
}
