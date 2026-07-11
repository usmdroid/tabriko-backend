package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.PlatformSettingsEntity;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.UserDevice;
import uz.tabriko.domain.enums.Platform;
import uz.tabriko.dto.request.UpdateProfileRequest;
import uz.tabriko.dto.response.UserResponse;
import uz.tabriko.repository.PlatformSettingsRepository;
import uz.tabriko.repository.UserDeviceRepository;
import uz.tabriko.repository.UserRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final UserDeviceRepository userDeviceRepo;
    private final PlatformSettingsRepository settingsRepo;
    private final UserMapper userMapper;

    public UserResponse getMe(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateMe(UUID userId, UpdateProfileRequest req) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (req.getName() != null) user.setName(req.getName());
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        return userMapper.toResponse(userRepo.save(user));
    }

    @Transactional
    public void registerFcmToken(UUID userId, String token, Platform platform, String appVersion,
                                  String deviceName, String osVersion, String deviceId, boolean rooted) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        UserDevice device;
        if (deviceId != null && !deviceId.isBlank()) {
            device = userDeviceRepo.findByUserIdAndDeviceId(userId, deviceId).orElse(new UserDevice());
        } else {
            device = userDeviceRepo.findByFcmToken(token).orElse(new UserDevice());
        }

        if (device.isBlocked()) {
            throw ApiException.forbidden("Device is blocked.");
        }

        device.setUser(user);
        device.setFcmToken(token);
        device.setPlatform(platform);
        device.setAppVersion(appVersion);
        device.setDeviceName(deviceName);
        device.setOsVersion(osVersion);
        device.setDeviceId(deviceId);
        device.setRooted(rooted);
        device.setUpdatedAt(Instant.now());

        PlatformSettingsEntity settings = settingsRepo.findById(1).orElseGet(PlatformSettingsEntity::new);
        if (settings.isBlockRootedDevices() && (device.isRooted() || Boolean.FALSE.equals(device.getGenuine()))) {
            throw ApiException.forbidden("Device is blocked.");
        }

        userDeviceRepo.save(device);

        user.setFcmToken(token);
        userRepo.save(user);
    }

    public User getOrThrow(UUID userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
