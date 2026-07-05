package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.request.UpdateCreatorKycRequest;
import uz.tabriko.dto.request.UpdateCreatorProfileRequest;
import uz.tabriko.dto.request.UpdatePayoutRequest;
import uz.tabriko.dto.request.UpdatePortfolioVisibilityRequest;
import uz.tabriko.dto.request.UpdateSocialRequest;
import uz.tabriko.dto.response.CreatorKycResponse;
import uz.tabriko.dto.response.CreatorSelfProfileResponse;
import uz.tabriko.dto.response.EarningsResponse;
import uz.tabriko.dto.response.PortfolioItemResponse;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.CreatorService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/creator")
@PreAuthorize("hasRole('CREATOR')")
@RequiredArgsConstructor
@Tag(name = "Creator Self-Service")
public class CreatorController {

    private final CreatorService creatorService;

    @GetMapping("/profile")
    @Operation(summary = "Get own creator profile")
    public ResponseEntity<BaseResponse<CreatorSelfProfileResponse>> getProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.ok(creatorService.getSelfProfile(principal.getUserId())));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update creator profile (bio, category, priceFrom, deliveryDays, options, accepting)")
    public ResponseEntity<BaseResponse<CreatorSelfProfileResponse>> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateCreatorProfileRequest req
    ) {
        return ResponseEntity.ok(BaseResponse.ok(creatorService.updateProfile(principal.getUserId(), req)));
    }

    @PutMapping(value = "/kyc/identity", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit ID document number and optional file")
    public ResponseEntity<BaseResponse<CreatorSelfProfileResponse>> updateKycIdentity(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String idNumber,
            @RequestParam(required = false) MultipartFile file
    ) {
        return ResponseEntity.ok(BaseResponse.ok(
                creatorService.updateKycIdentity(principal.getUserId(), idNumber, file)));
    }

    @PutMapping("/payout")
    @Operation(summary = "Set payout card or account details")
    public ResponseEntity<BaseResponse<CreatorSelfProfileResponse>> updatePayout(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UpdatePayoutRequest req
    ) {
        return ResponseEntity.ok(BaseResponse.ok(
                creatorService.updatePayout(principal.getUserId(), req)));
    }

    @PutMapping("/social")
    @Operation(summary = "Set social links (telegram, instagram)")
    public ResponseEntity<BaseResponse<CreatorSelfProfileResponse>> updateSocial(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UpdateSocialRequest req
    ) {
        return ResponseEntity.ok(BaseResponse.ok(
                creatorService.updateSocial(principal.getUserId(), req)));
    }

    // --- KYC (contract endpoints) ---

    @GetMapping("/kyc")
    @Operation(summary = "Get own KYC data (masked passportNumber and paymentCardNumber)")
    public ResponseEntity<BaseResponse<CreatorKycResponse>> getKyc(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.ok(creatorService.getKyc(principal.getUserId())));
    }

    @PutMapping("/kyc")
    @Operation(summary = "Update KYC data (passportNumber, passportFileUrl, paymentCardNumber, paymentHolderName, telegram, instagram)")
    public ResponseEntity<BaseResponse<CreatorKycResponse>> updateKyc(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UpdateCreatorKycRequest req
    ) {
        return ResponseEntity.ok(BaseResponse.ok(creatorService.updateKyc(principal.getUserId(), req)));
    }

    @PostMapping(value = "/kyc/passport-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload passport file; returns stored file url in passportFileUrl")
    public ResponseEntity<BaseResponse<CreatorKycResponse>> uploadPassportFile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(creatorService.uploadPassportFile(principal.getUserId(), file)));
    }

    @GetMapping("/portfolio")
    @Operation(summary = "Get own portfolio items")
    public ResponseEntity<BaseResponse<List<PortfolioItemResponse>>> getPortfolio(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.ok(creatorService.getPortfolio(principal.getUserId())));
    }

    @PostMapping(value = "/portfolio", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "Add portfolio item (via URL or file upload)")
    public ResponseEntity<BaseResponse<PortfolioItemResponse>> addPortfolio(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String mediaUrl,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(defaultValue = "true") boolean isPublic
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(
                        creatorService.addPortfolio(principal.getUserId(), mediaUrl, file, isPublic)));
    }

    @DeleteMapping("/portfolio/{id}")
    @Operation(summary = "Delete own portfolio item")
    public ResponseEntity<BaseResponse<Void>> deletePortfolio(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        creatorService.deletePortfolio(principal.getUserId(), id);
        return ResponseEntity.ok(BaseResponse.ok());
    }

    @PatchMapping("/portfolio/{id}/visibility")
    @Operation(summary = "Update portfolio item visibility")
    public ResponseEntity<BaseResponse<PortfolioItemResponse>> updateVisibility(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePortfolioVisibilityRequest req
    ) {
        return ResponseEntity.ok(BaseResponse.ok(
                creatorService.updateVisibility(principal.getUserId(), id, req.getIsPublic())));
    }

    @GetMapping("/earnings")
    @Operation(summary = "Get creator earnings summary")
    public ResponseEntity<BaseResponse<EarningsResponse>> getEarnings(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.ok(creatorService.getEarnings(principal.getUserId())));
    }
}
