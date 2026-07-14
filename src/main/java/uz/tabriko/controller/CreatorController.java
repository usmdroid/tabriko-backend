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
import uz.tabriko.domain.enums.OrderType;
import uz.tabriko.dto.request.AddCreatorRequisiteRequest;
import uz.tabriko.dto.request.CreateCreatorServiceRequest;
import uz.tabriko.dto.request.UpdateCreatorKycRequest;
import uz.tabriko.dto.request.UpdateCreatorProfileRequest;
import uz.tabriko.dto.request.UpdateCreatorServiceRequest;
import uz.tabriko.dto.request.UpdatePayoutRequest;
import uz.tabriko.dto.request.UpdatePortfolioVisibilityRequest;
import uz.tabriko.dto.request.UpdateSocialRequest;
import uz.tabriko.dto.response.CreatorKycResponse;
import uz.tabriko.dto.response.CreatorRequisiteResponse;
import uz.tabriko.dto.response.CreatorSelfProfileResponse;
import uz.tabriko.dto.response.CreatorServiceResponse;
import uz.tabriko.dto.response.EarningsResponse;
import uz.tabriko.dto.response.PortfolioItemResponse;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.CreatorService;
import uz.tabriko.service.RequisiteService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/creator")
@PreAuthorize("hasRole('CREATOR')")
@RequiredArgsConstructor
@Tag(name = "Creator Self-Service")
public class CreatorController {

    private final CreatorService creatorService;
    private final RequisiteService requisiteService;

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

    // --- Per-service pricing + discounts ---

    @GetMapping("/services")
    @Operation(summary = "Get own per-service pricing and discount configuration (only explicitly created offerings)")
    public ResponseEntity<BaseResponse<List<CreatorServiceResponse>>> getMyServices(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.ok(creatorService.getMyServices(principal.getUserId())));
    }

    @PostMapping("/services")
    @Operation(summary = "Create a service offering for a given type (VIDEO or AUDIO); defaults price=0, deliveryDays=3, accepting=false")
    public ResponseEntity<BaseResponse<CreatorServiceResponse>> createService(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCreatorServiceRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(creatorService.createService(principal.getUserId(), req.getType())));
    }

    @DeleteMapping("/services/{type}")
    @Operation(summary = "Delete own service offering for the given type")
    public ResponseEntity<BaseResponse<Void>> deleteService(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable OrderType type
    ) {
        creatorService.deleteService(principal.getUserId(), type);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(BaseResponse.ok());
    }

    @PatchMapping("/services/{type}")
    @Operation(summary = "Update price, delivery days, accepting flag and discount configuration for a service type")
    public ResponseEntity<BaseResponse<CreatorServiceResponse>> updateService(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable OrderType type,
            @Valid @RequestBody UpdateCreatorServiceRequest req
    ) {
        return ResponseEntity.ok(BaseResponse.ok(
                creatorService.updateService(principal.getUserId(), type, req)));
    }

    // --- Requisites ---

    @GetMapping("/requisites")
    @Operation(summary = "Get own requisite list for a service type (VIDEO or AUDIO)")
    public ResponseEntity<BaseResponse<List<CreatorRequisiteResponse>>> getRequisites(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam OrderType type
    ) {
        return ResponseEntity.ok(BaseResponse.ok(
                requisiteService.getCreatorRequisites(principal.getUserId(), type)));
    }

    @PostMapping("/requisites")
    @Operation(summary = "Add a requisite (from catalog or custom)")
    public ResponseEntity<BaseResponse<CreatorRequisiteResponse>> addRequisite(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddCreatorRequisiteRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(requisiteService.addCreatorRequisite(principal.getUserId(), req)));
    }

    @DeleteMapping("/requisites/{id}")
    @Operation(summary = "Delete own requisite")
    public ResponseEntity<BaseResponse<Void>> deleteRequisite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        requisiteService.deleteCreatorRequisite(principal.getUserId(), id);
        return ResponseEntity.ok(BaseResponse.ok());
    }
}
