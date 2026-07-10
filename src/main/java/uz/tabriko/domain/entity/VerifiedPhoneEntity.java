package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// Tracks phones that have exchanged a valid OTP for a longer-lived verifyToken,
// used by the creator-application flow (ApplicationService) between /verify-phone
// and /applications so the short OTP TTL doesn't expire mid-form.
@Entity
@Table(name = "verified_phones")
@Getter
@Setter
public class VerifiedPhoneEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "verify_token", nullable = false, length = 100)
    private String verifyToken;

    @Column(name = "ig_verify_code", length = 20)
    private String igVerifyCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
