package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.dto.request.LoginRequest;
import uz.tabriko.dto.request.RefreshTokenRequest;
import uz.tabriko.dto.request.RegisterRequest;
import uz.tabriko.dto.request.ResetPasswordRequest;
import uz.tabriko.dto.request.SendOtpRequest;
import uz.tabriko.dto.response.AuthResponse;
import uz.tabriko.dto.response.TokenResponse;
import uz.tabriko.dto.response.UserResponse;
import uz.tabriko.infrastructure.firebase.OtpService;
import uz.tabriko.repository.UserRepository;
import uz.tabriko.security.JwtUtil;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepo;
    @Mock OtpService otpService;
    @Mock JwtUtil jwtUtil;
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks AuthService authService;

    private static final String PHONE = "+998901234567";
    private static final String OTP = "123456";
    private static final String PASSWORD = "secret123";
    private static final String HASH = "$2a$10$someBcryptHashValue";
    private static final String ACCESS = "access-token";
    private static final String REFRESH = "refresh-token";

    @BeforeEach
    void stubJwt() {
        lenient().when(jwtUtil.generateAccessToken(any(), any(), any())).thenReturn(ACCESS);
        lenient().when(jwtUtil.generateRefreshToken(any())).thenReturn(REFRESH);
        lenient().when(userMapper.toResponse(any())).thenReturn(new UserResponse());
    }

    // --- register ---

    @Test
    void register_newPhone_validOtp_returnsAuthResponse_andSavesBcryptHash() {
        when(otpService.verifyOtp(PHONE, OTP)).thenReturn(true);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterRequest req = new RegisterRequest();
        req.setPhone(PHONE);
        req.setCode(OTP);
        req.setPassword(PASSWORD);
        req.setName("Test User");

        AuthResponse resp = authService.register(req);

        assertThat(resp.getAccessToken()).isEqualTo(ACCESS);
        assertThat(resp.getRefreshToken()).isEqualTo(REFRESH);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        User saved = captor.getValue();

        // Password must be stored as BCrypt hash, never plaintext
        assertThat(saved.getPasswordHash()).isEqualTo(HASH);
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(saved.getRole()).isEqualTo(Role.CLIENT);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void register_wrongOtp_throws400() {
        when(otpService.verifyOtp(PHONE, "000000")).thenReturn(false);

        RegisterRequest req = new RegisterRequest();
        req.setPhone(PHONE);
        req.setCode("000000");
        req.setPassword(PASSWORD);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void register_existingPhoneWithPassword_throws409() {
        when(otpService.verifyOtp(PHONE, OTP)).thenReturn(true);

        User existing = user(PHONE, HASH, UserStatus.ACTIVE);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.of(existing));

        RegisterRequest req = new RegisterRequest();
        req.setPhone(PHONE);
        req.setCode(OTP);
        req.setPassword(PASSWORD);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getMessage()).contains("allaqachon ro'yxatdan o'tgan");
                });
    }

    @Test
    void register_blockedUserNullHash_throws403() {
        // Blocked user with no password must NOT be able to set one via register
        when(otpService.verifyOtp(PHONE, OTP)).thenReturn(true);

        User blocked = user(PHONE, null, UserStatus.BLOCKED);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.of(blocked));

        RegisterRequest req = new RegisterRequest();
        req.setPhone(PHONE);
        req.setCode(OTP);
        req.setPassword(PASSWORD);
        req.setName("Test User");

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getStatus().value()).isEqualTo(403);
                    assertThat(ex.getMessage()).isEqualTo("Account is blocked");
                });

        // No token issued and passwordHash must NOT be set
        verify(userRepo, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void register_existingPhoneWithNullPassword_setsPasswordAndSucceeds() {
        // Admin-created user with no password can register via OTP
        when(otpService.verifyOtp(PHONE, OTP)).thenReturn(true);

        User existing = user(PHONE, null, UserStatus.ACTIVE);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterRequest req = new RegisterRequest();
        req.setPhone(PHONE);
        req.setCode(OTP);
        req.setPassword(PASSWORD);

        AuthResponse resp = authService.register(req);
        assertThat(resp.getAccessToken()).isEqualTo(ACCESS);

        verify(userRepo).save(argThat(u -> HASH.equals(u.getPasswordHash())));
    }

    // --- phone normalization ---

    @Test
    void sendOtp_rawPhoneFormat_normalizesBeforeStoring() {
        SendOtpRequest req = new SendOtpRequest();
        req.setPhone("998 90 123 45 67");

        authService.sendOtp(req);

        verify(otpService).sendOtp(PHONE);
    }

    @Test
    void register_rawPhoneFormat_normalizesForLookupAndSave() {
        String raw = "+998 90-123-45-67";
        when(otpService.verifyOtp(PHONE, OTP)).thenReturn(true);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterRequest req = new RegisterRequest();
        req.setPhone(raw);
        req.setCode(OTP);
        req.setPassword(PASSWORD);
        req.setName("Test User");

        authService.register(req);

        // Verified/looked-up under the normalized phone, not the raw input
        verify(otpService).verifyOtp(PHONE, OTP);
        verify(userRepo).findByPhone(PHONE);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getPhone()).isEqualTo(PHONE);
    }

    @Test
    void login_differentRawFormat_matchesUserRegisteredWithNormalizedPhone() {
        // A user stored with a normalized phone must still be found when the
        // client sends the same number in a different raw format.
        String differentRawFormat = "998901234567";
        User u = user(PHONE, HASH, UserStatus.ACTIVE);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        AuthResponse resp = authService.login(loginReq(differentRawFormat, PASSWORD));

        assertThat(resp.getAccessToken()).isEqualTo(ACCESS);
        verify(userRepo).findByPhone(PHONE);
    }

    // --- login ---

    @Test
    void login_correctCredentials_returns200() {
        User u = user(PHONE, HASH, UserStatus.ACTIVE);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        AuthResponse resp = authService.login(loginReq(PHONE, PASSWORD));

        assertThat(resp.getAccessToken()).isEqualTo(ACCESS);
        assertThat(resp.getRefreshToken()).isEqualTo(REFRESH);
    }

    @Test
    void login_wrongPassword_throws401_notOtherStatus() {
        User u = user(PHONE, HASH, UserStatus.ACTIVE);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrong", HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginReq(PHONE, "wrong")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getStatus().value()).isEqualTo(401);
                    assertThat(ex.getMessage()).isEqualTo("Telefon yoki parol xato");
                });
    }

    @Test
    void login_nonExistentPhone_throws401_sameMessage() {
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginReq(PHONE, PASSWORD)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getStatus().value()).isEqualTo(401);
                    assertThat(ex.getMessage()).isEqualTo("Telefon yoki parol xato");
                });
    }

    @Test
    void login_blockedUser_correctPassword_throws401_NOT403_noOracle() {
        // CRITICAL: BLOCKED user must return 401 with generic message, not 403
        // This prevents using login as a blocked-account oracle
        User blocked = user(PHONE, HASH, UserStatus.BLOCKED);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> authService.login(loginReq(PHONE, PASSWORD)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    // Must be 401, never 403
                    assertThat(ex.getStatus().value())
                            .as("BLOCKED user must get 401, not 403 (oracle prevention)")
                            .isEqualTo(401);
                    // Must be the generic message
                    assertThat(ex.getMessage())
                            .as("Message must not reveal account is blocked")
                            .isEqualTo("Telefon yoki parol xato");
                });

        // passwordEncoder must NOT be called — BLOCKED check short-circuits
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_nullPasswordHash_throws401() {
        // Admin-created user with no password set
        User u = user(PHONE, null, UserStatus.ACTIVE);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authService.login(loginReq(PHONE, PASSWORD)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(401));
    }

    // --- reset-password ---

    @Test
    void resetPassword_validOtpExistingUser_updatesPassword() {
        when(otpService.verifyOtp(PHONE, OTP)).thenReturn(true);
        User u = user(PHONE, HASH, UserStatus.ACTIVE);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.of(u));

        String newHash = "$2a$10$newHash";
        when(passwordEncoder.encode("newpass1")).thenReturn(newHash);
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setPhone(PHONE);
        req.setCode(OTP);
        req.setNewPassword("newpass1");

        AuthResponse resp = authService.resetPassword(req);
        assertThat(resp.getAccessToken()).isEqualTo(ACCESS);

        verify(userRepo).save(argThat(saved -> newHash.equals(saved.getPasswordHash())));
    }

    @Test
    void resetPassword_adminUserNullHash_setsFirstPassword_thenLoginWorks() {
        // Admin-created user (passwordHash=null) sets first password via reset-password
        when(otpService.verifyOtp(PHONE, OTP)).thenReturn(true);
        User u = user(PHONE, null, UserStatus.ACTIVE);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.of(u));

        String firstHash = "$2a$10$firstHash";
        when(passwordEncoder.encode("firstpass")).thenReturn(firstHash);
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setPhone(PHONE);
        req.setCode(OTP);
        req.setNewPassword("firstpass");

        authService.resetPassword(req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(cap.capture());
        assertThat(cap.getValue().getPasswordHash()).isEqualTo(firstHash);

        // Simulate subsequent login with the new password
        User updated = user(PHONE, firstHash, UserStatus.ACTIVE);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.of(updated));
        when(passwordEncoder.matches("firstpass", firstHash)).thenReturn(true);

        AuthResponse loginResp = authService.login(loginReq(PHONE, "firstpass"));
        assertThat(loginResp.getAccessToken()).isEqualTo(ACCESS);
    }

    @Test
    void resetPassword_blockedUser_throws403() {
        // Blocked user must NOT be able to reset password and re-enable the account
        when(otpService.verifyOtp(PHONE, OTP)).thenReturn(true);

        User blocked = user(PHONE, HASH, UserStatus.BLOCKED);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.of(blocked));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setPhone(PHONE);
        req.setCode(OTP);
        req.setNewPassword("newpass1");

        assertThatThrownBy(() -> authService.resetPassword(req))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getStatus().value()).isEqualTo(403);
                    assertThat(ex.getMessage()).isEqualTo("Account is blocked");
                });

        // No token issued and passwordHash must NOT be updated
        verify(userRepo, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void resetPassword_wrongOtp_throws400() {
        when(otpService.verifyOtp(PHONE, "000000")).thenReturn(false);

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setPhone(PHONE);
        req.setCode("000000");
        req.setNewPassword("newpass1");

        assertThatThrownBy(() -> authService.resetPassword(req))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void resetPassword_nonExistentUser_throws404() {
        when(otpService.verifyOtp(PHONE, OTP)).thenReturn(true);
        when(userRepo.findByPhone(PHONE)).thenReturn(Optional.empty());

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setPhone(PHONE);
        req.setCode(OTP);
        req.setNewPassword("newpass1");

        assertThatThrownBy(() -> authService.resetPassword(req))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    // --- verify-otp endpoint absent ---

    @Test
    void verifyOtpEndpoint_notDefinedInController() throws NoSuchMethodException {
        // Confirm no /verify-otp mapping exists on AuthController
        boolean hasVerifyOtp = java.util.Arrays.stream(
                uz.tabriko.controller.AuthController.class.getDeclaredMethods())
                .anyMatch(m -> {
                    org.springframework.web.bind.annotation.PostMapping pm =
                            m.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
                    if (pm == null) return false;
                    return java.util.Arrays.stream(pm.value())
                            .anyMatch(v -> v.contains("verify-otp"));
                });
        assertThat(hasVerifyOtp)
                .as("POST /api/v1/auth/verify-otp must NOT be defined (endpoint removed)")
                .isFalse();
    }

    // --- helpers ---

    private User user(String phone, String passwordHash, UserStatus status) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setPhone(phone);
        u.setPasswordHash(passwordHash);
        u.setRole(Role.CLIENT);
        u.setStatus(status);
        return u;
    }

    private LoginRequest loginReq(String phone, String password) {
        LoginRequest req = new LoginRequest();
        req.setPhone(phone);
        req.setPassword(password);
        return req;
    }
}
