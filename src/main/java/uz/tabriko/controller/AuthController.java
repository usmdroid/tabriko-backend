package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.common.util.PhoneUtil;
import uz.tabriko.dto.request.LoginRequest;
import uz.tabriko.dto.request.RefreshTokenRequest;
import uz.tabriko.dto.request.RegisterRequest;
import uz.tabriko.dto.request.ResetPasswordRequest;
import uz.tabriko.dto.request.SendOtpRequest;
import uz.tabriko.infrastructure.ratelimit.LoginRateLimiter;
import uz.tabriko.infrastructure.ratelimit.OtpRateLimiter;
import uz.tabriko.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final OtpRateLimiter otpRateLimiter;
    private final LoginRateLimiter loginRateLimiter;

    @PostMapping("/send-otp")
    @Operation(summary = "Send OTP to phone (used for register and reset-password)")
    public ResponseEntity<BaseResponse<Void>> sendOtp(@Valid @RequestBody SendOtpRequest req, HttpServletRequest request) {
        otpRateLimiter.checkAndRecord(PhoneUtil.normalize(req.getPhone()), clientIp(request));
        authService.sendOtp(req);
        return ResponseEntity.ok(BaseResponse.ok());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/register")
    @Operation(summary = "Register new client with phone, OTP, and password")
    public ResponseEntity<BaseResponse<?>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(BaseResponse.ok(authService.register(req)));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with phone and password")
    public ResponseEntity<BaseResponse<?>> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        loginRateLimiter.checkAndRecord(PhoneUtil.normalize(req.getPhone()), clientIp(request));
        return ResponseEntity.ok(BaseResponse.ok(authService.login(req)));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset (or set first) password using OTP")
    public ResponseEntity<BaseResponse<?>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        return ResponseEntity.ok(BaseResponse.ok(authService.resetPassword(req)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<BaseResponse<?>> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        return ResponseEntity.ok(BaseResponse.ok(authService.refresh(req)));
    }
}
