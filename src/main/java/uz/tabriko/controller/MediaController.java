package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.response.MediaUploadResponse;
import uz.tabriko.dto.response.SignedUrlResponse;
import uz.tabriko.security.JwtUtil;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.MediaService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Media")
public class MediaController {

    private final MediaService mediaService;
    private final JwtUtil jwtUtil;

    @PostMapping(value = "/orders/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CREATOR')")
    @Operation(summary = "Upload media for an order (video or audio)")
    public ResponseEntity<BaseResponse<MediaUploadResponse>> uploadMedia(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(mediaService.uploadMedia(principal.getUserId(), id, file)));
    }

    @GetMapping("/orders/{id}/download")
    @Operation(summary = "Get a signed download URL for accepted order (CLIENT or CREATOR)")
    public ResponseEntity<BaseResponse<SignedUrlResponse>> getDownloadUrl(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(BaseResponse.ok(mediaService.getDownloadUrl(principal.getUserId(), id)));
    }

    // Public endpoint — validates download token and redirects to the actual file URL
    @GetMapping("/media/signed")
    @Operation(summary = "Redirect to signed file (token-validated, no auth required)")
    public ResponseEntity<Void> redirectSigned(@RequestParam String token) {
        String fileUrl = jwtUtil.extractDownloadUrl(token);
        if (fileUrl == null) {
            throw ApiException.unauthorized("Invalid or expired download token");
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, fileUrl)
                .build();
    }
}
