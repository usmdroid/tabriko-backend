package uz.tabriko.service;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import uz.tabriko.infrastructure.firebase.OtpService;
import uz.tabriko.repository.UserRepository;
import uz.tabriko.security.JwtUtil;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final OtpService otpService;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public void sendOtp(SendOtpRequest req) {
        otpService.sendOtp(req.getPhone());
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (!otpService.verifyOtp(req.getPhone(), req.getCode())) {
            throw ApiException.badRequest("Invalid OTP code");
        }
        User user = userRepo.findByPhone(req.getPhone()).orElse(null);
        if (user != null && user.getPasswordHash() != null) {
            throw ApiException.conflict("Bu telefon raqami allaqachon ro'yxatdan o'tgan");
        }
        // A blocked account must not be able to re-enable itself by setting a password.
        if (user != null && user.getStatus() == UserStatus.BLOCKED) {
            throw ApiException.forbidden("Account is blocked");
        }
        if (user == null) {
            user = new User();
            user.setPhone(req.getPhone());
            user.setName(req.getName());
            user.setRole(Role.CLIENT);
            user.setStatus(UserStatus.ACTIVE);
        }
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        userRepo.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        // Generic error for user-not-found, blocked account, wrong password, or no password set.
        // The BLOCKED check is folded into the same generic failure so login cannot be used as a
        // password oracle for blocked accounts (401 for every failure, never a distinct 403).
        User user = userRepo.findByPhone(req.getPhone()).orElse(null);
        if (user == null
                || user.getStatus() == UserStatus.BLOCKED
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Telefon yoki parol xato");
        }
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest req) {
        // OTP verified before user lookup to prevent phone-number enumeration
        if (!otpService.verifyOtp(req.getPhone(), req.getCode())) {
            throw ApiException.badRequest("Invalid OTP code");
        }
        User user = userRepo.findByPhone(req.getPhone())
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

    private AuthResponse buildAuthResponse(User user) {
        String access = jwtUtil.generateAccessToken(user.getId(), user.getPhone(), user.getRole().name());
        String refresh = jwtUtil.generateRefreshToken(user.getId());
        return new AuthResponse(access, refresh, userMapper.toResponse(user));
    }
}
