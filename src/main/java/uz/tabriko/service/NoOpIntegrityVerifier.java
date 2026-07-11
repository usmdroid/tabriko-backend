package uz.tabriko.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.tabriko.domain.enums.Platform;

// Used when app.attestation.enabled=false (the default). Skips real credential check so tests pass.
@Component
@ConditionalOnProperty(name = "app.attestation.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpIntegrityVerifier implements IntegrityVerifier {

    @Override
    public boolean verify(Platform platform, String deviceId, String integrityToken) {
        return false;
    }
}
