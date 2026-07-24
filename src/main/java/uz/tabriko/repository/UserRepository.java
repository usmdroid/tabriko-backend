package uz.tabriko.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.UserStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhone(String phone);
    boolean existsByPhone(String phone);
    boolean existsByAccountNumber(String accountNumber);
    long countByRole(Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT u FROM User u WHERE u.phoneHash IN :hashes AND u.birthDate IS NOT NULL AND u.birthdayVisible = true AND u.id != :callerId")
    List<User> findBirthdayMatches(@Param("hashes") List<String> hashes, @Param("callerId") UUID callerId);

    // Admin: list CLIENT users with optional name/phone search and status filter
    @Query("""
        SELECT u FROM User u
        WHERE u.role = uz.tabriko.domain.enums.Role.CLIENT
          AND (:search IS NULL OR LOWER(u.name) LIKE :searchPattern OR u.phone LIKE :searchPattern)
          AND (:status IS NULL OR u.status = :status)
          AND (:status IS NOT NULL OR u.status <> uz.tabriko.domain.enums.UserStatus.DELETED)
        ORDER BY u.createdAt DESC
        """)
    List<User> findClientsFiltered(
        @Param("search") String search,
        @Param("searchPattern") String searchPattern,
        @Param("status") UserStatus status
    );

    // Admin "deleted accounts" tab — all roles, newest deletion first.
    List<User> findByStatusOrderByDeletedAtDesc(UserStatus status);
}
