package uz.tabriko.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.tabriko.domain.entity.Order;
import uz.tabriko.domain.enums.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByClientIdOrderByCreatedAtDesc(UUID clientId, Pageable pageable);

    Page<Order> findByCreatorIdOrderByCreatedAtDesc(UUID creatorId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.deadline < :now")
    List<Order> findOverdueOrders(OrderStatus status, Instant now);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Sum of order prices for creator's active (not yet released/refunded) orders
    @Query("SELECT COALESCE(SUM(o.price), 0) FROM Order o WHERE o.creator.id = :creatorId AND o.status IN :statuses")
    java.math.BigDecimal sumPriceByCreatorAndStatuses(
        @Param("creatorId") UUID creatorId,
        @Param("statuses") java.util.Collection<OrderStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    // Admin stats: sum price of orders with a given status (e.g. ACCEPTED = revenue)
    @Query("SELECT COALESCE(SUM(o.price), 0) FROM Order o WHERE o.status = :status")
    java.math.BigDecimal sumPriceByStatus(@Param("status") OrderStatus status);

    long countByStatus(OrderStatus status);
}
