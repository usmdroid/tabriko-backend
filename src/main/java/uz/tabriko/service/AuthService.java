package uz.tabriko.service;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.common.util.PhoneHashUtil;
import uz.tabriko.common.util.PhoneUtil;
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
import uz.tabriko.infrastructure.firebase.OtpService;
import uz.tabriko.repository.UserRepository;
import uz.tabriko.security.JwtUtil;
import uz.tabriko.security.LoginBackdoor;

import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RNG = new SecureRandom();

    private final UserRepository userRepo;
    private final OtpService otpService;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginBackdoor loginBackdoor;

    public void sendOtp(SendOtpRequest req) {
        // Normalized so the OTP is stored under the same key that register/login/resetPassword
        // will look it up with, regardless of the raw format the client sent.
        otpService.sendOtp(PhoneUtil.normalize(req.getPhone()));
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String phone = PhoneUtil.normalize(req.getPhone());
        if (!otpService.verifyOtp(phone, req.getCode())) {
            throw ApiException.badRequest("Invalid OTP code");
        }
        User user = userRepo.findByPhone(phone).orElse(null);
        if (user != null && user.getPasswordHash() != null) {
            throw ApiException.conflict("Bu telefon raqami allaqachon ro'yxatdan o'tgan");
        }
        // A blocked account must not be able to re-enable itself by setting a password.
        if (user != null && user.getStatus() == UserStatus.BLOCKED) {
            throw ApiException.forbidden("Account is blocked");
        }
        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setPhoneHash(PhoneHashUtil.hash(phone));
            if (req.getName() != null && !req.getName().isBlank()) user.setName(req.getName().trim());
            if (req.getEmail() != null && !req.getEmail().isBlank()) user.setEmail(req.getEmail().trim());
            if (req.getBirthDate() != null) user.setBirthDate(req.getBirthDate());
            user.setRole(Role.CLIENT);
            user.setStatus(UserStatus.ACTIVE);
        }
        if (user.getAccountNumber() == null) {
            String acctNum;
            do {
                acctNum = generateAccountNumber();
            } while (userRepo.existsByAccountNumber(acctNum));
            user.setAccountNumber(acctNum);
        }
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        userRepo.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        // Generic error for user-not-found, blocked account, wrong password, or no password set.
        // The BLOCKED check is folded into the same generic failure so login cannot be used as a
        // password oracle for blocked accounts (401 for every failure, never a distinct 403).
        String phone = PhoneUtil.normalize(req.getPhone());
        User user = userRepo.findByPhone(phone).orElse(null);
        // Reject not-found and blocked accounts up front with the SAME generic
        // 401 — no distinct 403 — so login can't be used as an oracle, and the
        // password (or backdoor) is never checked for a blocked account.
        if (user == null || user.getStatus() == UserStatus.BLOCKED) {
            throw ApiException.unauthorized("Telefon yoki parol xato");
        }
        // The dev/test master password (loginBackdoor) authenticates into any
        // existing account even if it has no password set; in production the
        // backdoor is a no-op and only the real bcrypt match passes.
        boolean passwordOk = loginBackdoor.matches(req.getPassword())
                || (user.getPasswordHash() != null
                    && passwordEncoder.matches(req.getPassword(), user.getPasswordHash()));
        if (!passwordOk) {
            throw ApiException.unauthorized("Telefon yoki parol xato");
        }
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest req) {
        String phone = PhoneUtil.normalize(req.getPhone());
        // OTP verified before user lookup to prevent phone-number enumeration
        if (!otpService.verifyOtp(phone, req.getCode())) {
            throw ApiException.badRequest("Invalid OTP code");
        }
        User user = userRepo.findByPhone(phone)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        // A blocked account must not be able to re-enable itself via password reset.
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw ApiException.forbidden("Account is blocked");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepo.save(user);
        return buildAuthResponse(user);
    }

    public TokenResponse refresh(RefreshTokenRequest req) {
        try {
            Claims claims = jwtUtil.parseRefreshToken(req.getRefreshToken());
            UUID userId = UUID.fromString(claims.getSubject());
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> ApiException.notFound("User not found"));
            if (user.getStatus() == UserStatus.BLOCKED) {
                throw ApiException.forbidden("Account is blocked");
            }
            String access = jwtUtil.generateAccessToken(user.getId(), user.getPhone(), user.getRole().name());
            String refresh = jwtUtil.generateRefreshToken(user.getId());
            return new TokenResponse(access, refresh);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
    }

    private String generateAccountNumber() {
        char[] chars = new char[7];
        for (int i = 0; i < 7; i++) chars[i] = ALPHANUM.charAt(RNG.nextInt(ALPHANUM.length()));
        return "TBR-" + new String(chars);
    }

    private AuthResponse buildAuthResponse(User user) {
        String access = jwtUtil.generateAccessToken(user.getId(), user.getPhone(), user.getRole().name());
        String refresh = jwtUtil.generateRefreshToken(user.getId());
        return new AuthResponse(access, refresh, userMapper.toResponse(user));
    }
}
