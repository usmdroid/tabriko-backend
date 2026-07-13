package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.domain.entity.PlatformSettingsEntity;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.UserDevice;
import uz.tabriko.domain.enums.Platform;
import uz.tabriko.infrastructure.firebase.OtpService;
import uz.tabriko.repository.PlatformSettingsRepository;
import uz.tabriko.repository.UserDeviceRepository;
import uz.tabriko.repository.UserRepository;

import uz.tabriko.common.exception.ApiException;
import uz.tabriko.dto.request.UpdateProfileRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepo;
    @Mock UserDeviceRepository userDeviceRepo;
    @Mock PlatformSettingsRepository settingsRepo;
    @Mock UserMapper userMapper;
    @Mock OtpService otpService;

    @InjectMocks UserService userService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        // lenient: not every test (e.g. requestPhoneChange, and the invalid-OTP
        // confirm path) reaches a save() call.
        lenient().when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userDeviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(settingsRepo.findById(1)).thenReturn(Optional.of(new PlatformSettingsEntity()));
    }

    // --- updateMe ---

    @Test
    void updateMe_withBirthDate_persistsBirthDate() {
        LocalDate dob = LocalDate.of(1990, 3, 20);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setBirthDate(dob);

        userService.updateMe(userId, req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getBirthDate()).isEqualTo(dob);
    }

    @Test
    void updateMe_nullBirthDate_doesNotClearExistingBirthDate() {
        LocalDate existing = LocalDate.of(1990, 3, 20);
        user.setBirthDate(existing);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("New Name");
        // birthDate is null — should not be cleared

        userService.updateMe(userId, req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getBirthDate()).isEqualTo(existing);
    }

    @Test
    void registerFcmToken_newToken_createsDeviceRow() {
        when(userDeviceRepo.findByFcmToken("new-token")).thenReturn(Optional.empty());

        userService.registerFcmToken(userId, "new-token", Platform.ANDROID, "1.0.0", null, null, null, false);

        ArgumentCaptor<UserDevice> deviceCaptor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepo).save(deviceCaptor.capture());
        UserDevice saved = deviceCaptor.getValue();
        assertThat(saved.getFcmToken()).isEqualTo("new-token");
        assertThat(saved.getPlatform()).isEqualTo(Platform.ANDROID);
        assertThat(saved.getAppVersion()).isEqualTo("1.0.0");
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void registerFcmToken_existingToken_updatesDevice() {
        UserDevice existing = new UserDevice();
        existing.setFcmToken("existing-token");
        existing.setUser(new User());
        existing.setPlatform(Platform.IOS);
        existing.setAppVersion("0.9.0");
        existing.setCreatedAt(Instant.now().minusSeconds(100));

        when(userDeviceRepo.findByFcmToken("existing-token")).thenReturn(Optional.of(existing));

        userService.registerFcmToken(userId, "existing-token", Platform.ANDROID, "2.0.0", null, null, null, false);

        ArgumentCaptor<UserDevice> deviceCaptor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepo).save(deviceCaptor.capture());
        UserDevice saved = deviceCaptor.getValue();
        assertThat(saved.getPlatform()).isEqualTo(Platform.ANDROID);
        assertThat(saved.getAppVersion()).isEqualTo("2.0.0");
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void registerFcmToken_sameTokenDifferentUser_reassignsDevice() {
        User anotherUser = new User();
        anotherUser.setId(UUID.randomUUID());

        UserDevice existing = new UserDevice();
        existing.setFcmToken("shared-token");
        existing.setUser(anotherUser);
        existing.setPlatform(Platform.IOS);
        existing.setAppVersion("1.0.0");
        existing.setCreatedAt(Instant.now().minusSeconds(100));

        when(userDeviceRepo.findByFcmToken("shared-token")).thenReturn(Optional.of(existing));

        userService.registerFcmToken(userId, "shared-token", Platform.IOS, "1.0.0", null, null, null, false);

        ArgumentCaptor<UserDevice> deviceCaptor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepo).save(deviceCaptor.capture());
        assertThat(deviceCaptor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void registerFcmToken_alsoUpdatesUserFcmTokenField() {
        when(userDeviceRepo.findByFcmToken("my-token")).thenReturn(Optional.empty());

        userService.registerFcmToken(userId, "my-token", Platform.ANDROID, "1.0.0", null, null, null, false);

        assertThat(user.getFcmToken()).isEqualTo("my-token");
        verify(userRepo).save(user);
    }

    @Test
    void registerFcmToken_withDeviceNameAndOsVersion_persistsNewFields() {
        when(userDeviceRepo.findByFcmToken("token-x")).thenReturn(Optional.empty());

        userService.registerFcmToken(userId, "token-x", Platform.IOS, "2.1.0", "iPhone 14", "iOS 16.5", null, false);

        ArgumentCaptor<UserDevice> deviceCaptor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepo).save(deviceCaptor.capture());
        UserDevice saved = deviceCaptor.getValue();
        assertThat(saved.getDeviceName()).isEqualTo("iPhone 14");
        assertThat(saved.getOsVersion()).isEqualTo("iOS 16.5");
    }

    @Test
    void registerFcmToken_upsert_updatesDeviceNameAndOsVersion() {
        UserDevice existing = new UserDevice();
        existing.setFcmToken("token-y");
        existing.setUser(user);
        existing.setPlatform(Platform.ANDROID);
        existing.setAppVersion("1.0.0");
        existing.setDeviceName("Old Phone");
        existing.setOsVersion("Android 12");
        existing.setCreatedAt(Instant.now().minusSeconds(100));

        when(userDeviceRepo.findByFcmToken("token-y")).thenReturn(Optional.of(existing));

        userService.registerFcmToken(userId, "token-y", Platform.ANDROID, "2.0.0", "New Phone", "Android 14", null, false);

        ArgumentCaptor<UserDevice> deviceCaptor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepo).save(deviceCaptor.capture());
        UserDevice saved = deviceCaptor.getValue();
        assertThat(saved.getDeviceName()).isEqualTo("New Phone");
        assertThat(saved.getOsVersion()).isEqualTo("Android 14");
    }

    // --- requestPhoneChange / confirmPhoneChange ---

    @Test
    void requestPhoneChange_newUnusedNumber_sendsOtp() {
        user.setPhone("+998901111111");
        when(userRepo.findByPhone("+998902222222")).thenReturn(Optional.empty());

        userService.requestPhoneChange(userId, "+998902222222");

        verify(otpService).sendOtp("+998902222222");
    }

    @Test
    void requestPhoneChange_sameAsCurrentNumber_throwsBadRequest() {
        user.setPhone("+998901111111");

        assertThatThrownBy(() -> userService.requestPhoneChange(userId, "+998901111111"))
                .isInstanceOf(ApiException.class);

        verify(otpService, never()).sendOtp(any());
    }

    @Test
    void requestPhoneChange_numberOwnedByAnotherUser_throwsConflict() {
        user.setPhone("+998901111111");
        User another = new User();
        another.setId(UUID.randomUUID());
        when(userRepo.findByPhone("+998902222222")).thenReturn(Optional.of(another));

        assertThatThrownBy(() -> userService.requestPhoneChange(userId, "+998902222222"))
                .isInstanceOf(ApiException.class);

        verify(otpService, never()).sendOtp(any());
    }

    @Test
    void confirmPhoneChange_validOtp_updatesPhone() {
        user.setPhone("+998901111111");
        when(otpService.verifyOtp("+998902222222", "1234")).thenReturn(true);
        when(userRepo.findByPhone("+998902222222")).thenReturn(Optional.empty());
        when(userMapper.toResponse(any())).thenReturn(new uz.tabriko.dto.response.UserResponse());

        userService.confirmPhoneChange(userId, "+998902222222", "1234");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getPhone()).isEqualTo("+998902222222");
    }

    @Test
    void confirmPhoneChange_invalidOtp_throwsAndDoesNotSave() {
        user.setPhone("+998901111111");
        when(otpService.verifyOtp(eq("+998902222222"), any())).thenReturn(false);

        assertThatThrownBy(() -> userService.confirmPhoneChange(userId, "+998902222222", "0000"))
                .isInstanceOf(ApiException.class);

        verify(userRepo, never()).save(any());
    }
}
