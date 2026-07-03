package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.request.RefreshTokenRequest;
import uz.tabriko.dto.request.SendOtpRequest;
import uz.tabriko.dto.request.VerifyOtpRequest;
import uz.tabriko.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/send-otp")
    @Operation(summary = "Send OTP to phone")
    public ResponseEntity<BaseResponse<Void>> sendOtp(@Valid @RequestBody SendOtpRequest req) {
        authService.sendOtp(req);
        return ResponseEntity.ok(BaseResponse.ok());
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP and get tokens")
    public ResponseEntity<BaseResponse<?>> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return ResponseEntity.ok(BaseResponse.ok(authService.verifyOtp(req)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<BaseResponse<?>> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        return ResponseEntity.ok(BaseResponse.ok(authService.refresh(req)));
    }
}
