package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.request.AdminApplicationDecisionRequest;
import uz.tabriko.dto.request.ReplyApplicationRequest;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.ApplicationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/applications")
@RequiredArgsConstructor
@Tag(name = "Admin — Applications")
@PreAuthorize("hasAnyRole('SUPERADMIN', 'MODERATOR')")
public class AdminApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    @Operation(summary = "List applications with optional status filter")
    public ResponseEntity<BaseResponse<?>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(BaseResponse.ok(applicationService.listAdmin(status, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get application detail with thread")
    public ResponseEntity<BaseResponse<?>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ok(applicationService.getAdminDetail(id)));
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Move application to UNDER_REVIEW")
    public ResponseEntity<BaseResponse<?>> review(@PathVariable UUID id) {
        applicationService.markUnderReview(id);
        return ResponseEntity.ok(BaseResponse.ok());
    }

    @PostMapping("/{id}/request-info")
    @Operation(summary = "Request more info from applicant (INFO_REQUESTED + moderator message)")
    public ResponseEntity<BaseResponse<?>> requestInfo(
            @PathVariable UUID id,
            @Valid @RequestBody AdminApplicationDecisionRequest req
    ) {
        applicationService.requestInfo(id, req);
        return ResponseEntity.ok(BaseResponse.ok());
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject the application")
    public ResponseEntity<BaseResponse<?>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody AdminApplicationDecisionRequest req
    ) {
        applicationService.reject(id, req);
        return ResponseEntity.ok(BaseResponse.ok());
    }

    @PostMapping("/{id}/confirm-instagram")
    @Operation(summary = "Confirm Instagram ownership (ig_ownership_confirmed = true)")
    public ResponseEntity<BaseResponse<?>> confirmInstagram(@PathVariable UUID id) {
        applicationService.confirmInstagram(id);
        return ResponseEntity.ok(BaseResponse.ok());
    }

    @PostMapping("/{id}/message")
    @Operation(summary = "Send a moderator message in the application thread")
    public ResponseEntity<BaseResponse<?>> message(
            @PathVariable UUID id,
            @Valid @RequestBody ReplyApplicationRequest req
    ) {
        applicationService.sendModeratorMessage(id, req);
        return ResponseEntity.ok(BaseResponse.ok());
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve application: find-or-create user as CREATOR, create CreatorProfile")
    public ResponseEntity<BaseResponse<?>> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        applicationService.approve(id, principal);
        return ResponseEntity.ok(BaseResponse.ok());
    }
}
