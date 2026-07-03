package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deliveries")
@Getter
@Setter
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    // URL of watermarked media (shown before client accepts)
    @Column(name = "media_url_watermarked", nullable = false, length = 500)
    private String mediaUrlWatermarked;

    // URL of clean media (revealed after client accepts)
    @Column(name = "media_url_clean", length = 500)
    private String mediaUrlClean;

    @Column(nullable = false)
    private boolean watermarked = true;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt = Instant.now();
}
