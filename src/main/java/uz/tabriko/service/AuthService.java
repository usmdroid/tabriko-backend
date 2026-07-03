package uz.tabriko.service;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.dto.request.SendOtpRequest;
import uz.tabriko.dto.request.VerifyOtpRequest;
import uz.tabriko.dto.request.RefreshTokenRequest;
import uz.tabriko.dto.response.AuthResponse;
import uz.tabriko.dto.response.TokenResponse;
import uz.tabriko.dto.response.UserResponse;
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

    public void sendOtp(SendOtpRequest req) {
        otpService.sendOtp(req.getPhone());
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest req) {
        if (!otpService.verifyOtp(req.getPhone(), req.getCode())) {
            throw ApiException.badRequest("Invalid OTP code");
        }
        User user = userRepo.findByPhone(req.getPhone()).orElseGet(() -> {
            User u = new User();
            u.setPhone(req.getPhone());
            u.setRole(Role.CLIENT);
            u.setStatus(UserStatus.ACTIVE);
            return userRepo.save(u);
        });
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw ApiException.forbidden("Account is blocked");
        }
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
