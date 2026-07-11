package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.DeviceAttestNonce;
import uz.tabriko.domain.entity.UserDevice;
import uz.tabriko.domain.enums.Platform;
import uz.tabriko.repository.DeviceAttestNonceRepository;
import uz.tabriko.repository.UserDeviceRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceAttestService {

    private static final int NONCE_TTL_SECONDS = 300;
    private final SecureRandom secureRandom = new SecureRandom();

    private final DeviceAttestNonceRepository nonceRepo;
    private final UserDeviceRepository deviceRepo;
    private final IntegrityVerifier integrityVerifier;

    @Transactional
    public String generateNonce(String deviceId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String nonce = HexFormat.of().formatHex(bytes);

        DeviceAttestNonce entity = new DeviceAttestNonce();
        entity.setNonce(nonce);
        entity.setDeviceId(deviceId);
        entity.setExpiresAt(Instant.now().plusSeconds(NONCE_TTL_SECONDS));
        nonceRepo.save(entity);

        return nonce;
    }

    @Transactional
    public boolean attest(UUID userId, String deviceId, Platform platform, String integrityToken, String nonce) {
        nonceRepo.deleteByExpiresAtBefore(Instant.now());

        int updated = nonceRepo.markUsed(nonce, deviceId);
        if (updated == 0) {
            throw ApiException.badRequest("Invalid, expired, or already-used nonce.");
        }

        boolean genuine = integrityVerifier.verify(platform, deviceId, integrityToken);

        UserDevice device = deviceRepo.findByUserIdAndDeviceId(userId, deviceId)
                .orElseThrow(() -> ApiException.notFound("Device not found. Register FCM token first."));
        device.setGenuine(genuine);
        device.setRooted(!genuine);
        device.setUpdatedAt(Instant.now());
        deviceRepo.save(device);

        return genuine;
    }
}
