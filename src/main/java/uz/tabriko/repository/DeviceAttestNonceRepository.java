package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.tabriko.domain.entity.DeviceAttestNonce;

import java.time.Instant;
import java.util.Optional;

public interface DeviceAttestNonceRepository extends JpaRepository<DeviceAttestNonce, String> {
    void deleteByExpiresAtBefore(Instant cutoff);
    Optional<DeviceAttestNonce> findByNonceAndDeviceId(String nonce, String deviceId);

    @Modifying
    @Query("UPDATE DeviceAttestNonce n SET n.used = true WHERE n.nonce = :nonce AND n.deviceId = :deviceId AND n.used = false")
    int markUsed(@Param("nonce") String nonce, @Param("deviceId") String deviceId);
}
