package uz.tabriko.service;

import uz.tabriko.domain.enums.Platform;

public interface IntegrityVerifier {
    boolean verify(Platform platform, String deviceId, String integrityToken);
}
