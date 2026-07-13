package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.request.ConfirmPhoneChangeRequest;
import uz.tabriko.dto.request.RegisterFcmTokenRequest;
import uz.tabriko.dto.request.RequestPhoneChangeRequest;
import uz.tabriko.dto.request.UpdateProfileRequest;
import uz.tabriko.dto.response.UserResponse;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.UserService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "User")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<BaseResponse<?>> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.ok(userService.getMe(principal.getUserId())));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<BaseResponse<?>> updateMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest req
    ) {
        return ResponseEntity.ok(BaseResponse.ok(userService.updateMe(principal.getUserId(), req)));
    }

    @PostMapping("/me/phone/otp")
    @Operation(summary = "Send an OTP to a new phone number to start changing it")
    public ResponseEntity<BaseResponse<Void>> requestPhoneChange(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RequestPhoneChangeRequest req
    ) {
        userService.requestPhoneChange(principal.getUserId(), req.getNewPhone());
        return ResponseEntity.ok(BaseResponse.ok());
    }

    @PostMapping("/me/phone/confirm")
    @Operation(summary = "Verify the OTP and apply the new phone number")
    public ResponseEntity<BaseResponse<UserResponse>> confirmPhoneChange(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ConfirmPhoneChangeRequest req
    ) {
        return ResponseEntity.ok(BaseResponse.ok(
                userService.confirmPhoneChange(principal.getUserId(), req.getNewPhone(), req.getCode())));
    }

    @PostMapping("/devices/token")
    @Operation(summary = "Register FCM device token")
    public ResponseEntity<BaseResponse<Void>> registerToken(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RegisterFcmTokenRequest req
    ) {
        userService.registerFcmToken(principal.getUserId(), req.getToken(), req.getPlatform(), req.getAppVersion(),
                req.getDeviceName(), req.getOsVersion(), req.getDeviceId(), req.isRooted());
        return ResponseEntity.ok(BaseResponse.ok());
    }
}
