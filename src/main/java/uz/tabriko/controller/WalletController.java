package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.request.TopUpRequest;
import uz.tabriko.dto.request.WalletCallbackRequest;
import uz.tabriko.dto.request.WithdrawRequest;
import uz.tabriko.dto.response.TopUpInitResponse;
import uz.tabriko.dto.response.WalletResponse;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.WalletService;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet")
public class WalletController {

    private final WalletService walletService;

    @Value("${app.payment.callback-enabled:false}")
    private boolean callbackEnabled;

    @GetMapping
    @Operation(summary = "Get wallet balance, hold and transaction history")
    public ResponseEntity<BaseResponse<WalletResponse>> getWallet(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(BaseResponse.ok(walletService.getWallet(principal.getUserId(), page, size)));
    }

    @PostMapping("/topup")
    @Operation(summary = "Initiate a wallet top-up via Click or Payme (STUB)")
    public ResponseEntity<BaseResponse<TopUpInitResponse>> topUp(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TopUpRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(walletService.topUp(principal.getUserId(), req)));
    }

    // Public — called by payment provider webhook
    // TODO: verify HMAC signature from provider; enable via PAYMENT_CALLBACK_ENABLED env var
    @PostMapping("/callback")
    @Operation(summary = "Payment provider callback to confirm topup (public, no auth)")
    public ResponseEntity<BaseResponse<Void>> callback(@Valid @RequestBody WalletCallbackRequest req) {
        if (!callbackEnabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(BaseResponse.error(503, 503, "Payment callback is not configured"));
        }
        walletService.handleCallback(req);
        return ResponseEntity.ok(BaseResponse.ok());
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasRole('CREATOR')")
    @Operation(summary = "Request a payout (CREATOR only)")
    public ResponseEntity<BaseResponse<Void>> withdraw(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody WithdrawRequest req
    ) {
        walletService.withdraw(principal.getUserId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.created(null));
    }
}
