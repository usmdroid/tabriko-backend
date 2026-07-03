package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
