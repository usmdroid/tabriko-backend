package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.request.AttestRequest;
import uz.tabriko.dto.request.NonceRequest;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.DeviceAttestService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Device Attestation")
public class DeviceController {

    private final DeviceAttestService deviceAttestService;

    @PostMapping("/attest/nonce")
    @Operation(summary = "Generate a one-time nonce for device attestation")
    public ResponseEntity<BaseResponse<?>> getNonce(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody NonceRequest req
    ) {
        String nonce = deviceAttestService.generateNonce(req.getDeviceId());
        return ResponseEntity.ok(BaseResponse.ok(Map.of("nonce", nonce)));
    }

    @PostMapping("/attest")
    @Operation(summary = "Submit attestation token and record device integrity verdict")
    public ResponseEntity<BaseResponse<?>> attest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AttestRequest req
    ) {
        boolean genuine = deviceAttestService.attest(
                principal.getUserId(), req.getDeviceId(), req.getPlatform(),
                req.getIntegrityToken(), req.getNonce());
        return ResponseEntity.ok(BaseResponse.ok(Map.of("genuine", genuine)));
    }
}
