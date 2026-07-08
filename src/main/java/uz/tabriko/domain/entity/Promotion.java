package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "promotions")
@Getter
@Setter
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 300)
    private String subtitle;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 16)
    private String color;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "external_url", length = 500)
    private String externalUrl;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
