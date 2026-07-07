package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.ApplicationMessage;
import uz.tabriko.domain.entity.CreatorApplication;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationMessageRepository extends JpaRepository<ApplicationMessage, UUID> {

    List<ApplicationMessage> findByApplicationOrderByCreatedAtAsc(CreatorApplication application);
}
