package uz.tabriko.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.WalletTransaction;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.domain.enums.TransactionStatus;
import uz.tabriko.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<WalletTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<WalletTransaction> findByIdAndUserId(Long id, UUID userId);

    boolean existsByProviderRef(String providerRef);

    Optional<WalletTransaction> findByProviderRef(String providerRef);

    // Sum by user, status, and type set — used for balance calculation
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM WalletTransaction t " +
           "WHERE t.user.id = :userId AND t.status = :status AND t.type IN :types")
    BigDecimal sumByUserAndStatusAndTypes(
        @Param("userId") UUID userId,
        @Param("status") TransactionStatus status,
        @Param("types") Collection<TransactionType> types
    );

    // Active hold: HOLD txs linked to orders still in open states
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM WalletTransaction t " +
           "WHERE t.user.id = :userId AND t.type = :holdType " +
           "AND t.order.id IN (SELECT o.id FROM Order o WHERE o.status IN :activeStatuses)")
    BigDecimal computeActiveHold(
        @Param("userId") UUID userId,
        @Param("holdType") TransactionType holdType,
        @Param("activeStatuses") Collection<OrderStatus> activeStatuses
    );

    // Creator earnings: sum of RELEASE txs
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM WalletTransaction t " +
           "WHERE t.user.id = :userId AND t.type = :type AND t.status = :status")
    BigDecimal sumByUserAndTypeAndStatus(
        @Param("userId") UUID userId,
        @Param("type") TransactionType type,
        @Param("status") TransactionStatus status
    );

    // Held amount for a specific order
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM WalletTransaction t WHERE t.order.id = :orderId AND t.type = :type")
    BigDecimal sumByOrderIdAndType(UUID orderId, TransactionType type);
}
