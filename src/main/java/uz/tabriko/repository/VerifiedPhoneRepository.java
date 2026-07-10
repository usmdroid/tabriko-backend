package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.VerifiedPhoneEntity;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface VerifiedPhoneRepository extends JpaRepository<VerifiedPhoneEntity, Long> {

    Optional<VerifiedPhoneEntity> findByPhone(String phone);

    void deleteByPhone(String phone);

    @Modifying
    @Query("DELETE FROM VerifiedPhoneEntity v WHERE v.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
