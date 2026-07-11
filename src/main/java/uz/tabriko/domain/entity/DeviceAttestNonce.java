package uz.tabriko.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "device_attest_nonces")
@Getter
@Setter
public class DeviceAttestNonce {

    @Id
    @Column(name = "nonce", length = 64)
    private String nonce;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used = false;
}
