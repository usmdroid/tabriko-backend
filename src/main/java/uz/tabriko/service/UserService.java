package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.PlatformSettingsEntity;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.UserDevice;
import uz.tabriko.common.util.PhoneUtil;
import uz.tabriko.domain.enums.Platform;
import uz.tabriko.dto.request.UpdateProfileRequest;
import uz.tabriko.dto.response.UserResponse;
import uz.tabriko.infrastructure.firebase.OtpService;
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
    private final OtpService otpService;

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
        // null birthDate means "no change"; clients must send the field explicitly to update it
        if (req.getBirthDate() != null) user.setBirthDate(req.getBirthDate());
        return userMapper.toResponse(userRepo.save(user));
    }

    /**
     * Phone is treated as personal data like name/email, but changing it must be
     * OTP-verified (it is the login identifier), so it is a two-step flow instead
     * of a plain field on {@link #updateMe}: request an OTP to the NEW number,
     * then confirm it via {@link #confirmPhoneChange}.
     */
    public void requestPhoneChange(UUID userId, String newPhoneRaw) {
        User user = getOrThrow(userId);
        String newPhone = PhoneUtil.normalize(newPhoneRaw);
        if (newPhone.equals(user.getPhone())) {
            throw ApiException.badRequest("Bu raqam allaqachon sizning hisobingizga bog'langan");
        }
        User owner = userRepo.findByPhone(newPhone).orElse(null);
        if (owner != null && !owner.getId().equals(userId)) {
            throw ApiException.conflict("Bu telefon raqami boshqa hisobda ishlatilmoqda");
        }
        otpService.sendOtp(newPhone);
    }

    @Transactional
    public UserResponse confirmPhoneChange(UUID userId, String newPhoneRaw, String code) {
        User user = getOrThrow(userId);
        String newPhone = PhoneUtil.normalize(newPhoneRaw);
        if (!otpService.verifyOtp(newPhone, code)) {
            throw ApiException.badRequest("Invalid OTP code");
        }
        // Re-check uniqueness at confirm time too — the number could have been
        // claimed by someone else between the OTP request and this call.
        User owner = userRepo.findByPhone(newPhone).orElse(null);
        if (owner != null && !owner.getId().equals(userId)) {
            throw ApiException.conflict("Bu telefon raqami boshqa hisobda ishlatilmoqda");
        }
        user.setPhone(newPhone);
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
