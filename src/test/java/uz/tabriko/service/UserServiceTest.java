package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.UserDevice;
import uz.tabriko.domain.enums.Platform;
import uz.tabriko.repository.UserDeviceRepository;
import uz.tabriko.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepo;
    @Mock UserDeviceRepository userDeviceRepo;
    @Mock UserMapper userMapper;

    @InjectMocks UserService userService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userDeviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registerFcmToken_newToken_createsDeviceRow() {
        when(userDeviceRepo.findByFcmToken("new-token")).thenReturn(Optional.empty());

        userService.registerFcmToken(userId, "new-token", Platform.ANDROID, "1.0.0", null, null);

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

        userService.registerFcmToken(userId, "existing-token", Platform.ANDROID, "2.0.0", null, null);

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

        userService.registerFcmToken(userId, "shared-token", Platform.IOS, "1.0.0", null, null);

        ArgumentCaptor<UserDevice> deviceCaptor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepo).save(deviceCaptor.capture());
        assertThat(deviceCaptor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void registerFcmToken_alsoUpdatesUserFcmTokenField() {
        when(userDeviceRepo.findByFcmToken("my-token")).thenReturn(Optional.empty());

        userService.registerFcmToken(userId, "my-token", Platform.ANDROID, "1.0.0", null, null);

        assertThat(user.getFcmToken()).isEqualTo("my-token");
        verify(userRepo).save(user);
    }

    @Test
    void registerFcmToken_withDeviceNameAndOsVersion_persistsNewFields() {
        when(userDeviceRepo.findByFcmToken("token-x")).thenReturn(Optional.empty());

        userService.registerFcmToken(userId, "token-x", Platform.IOS, "2.1.0", "iPhone 14", "iOS 16.5");

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

        userService.registerFcmToken(userId, "token-y", Platform.ANDROID, "2.0.0", "New Phone", "Android 14");

        ArgumentCaptor<UserDevice> deviceCaptor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepo).save(deviceCaptor.capture());
        UserDevice saved = deviceCaptor.getValue();
        assertThat(saved.getDeviceName()).isEqualTo("New Phone");
        assertThat(saved.getOsVersion()).isEqualTo("Android 14");
    }
}
