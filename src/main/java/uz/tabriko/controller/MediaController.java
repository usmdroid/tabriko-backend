package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
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
import uz.tabriko.infrastructure.media.MediaStorageService;
import uz.tabriko.security.JwtUtil;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.MediaService;

import java.net.URLConnection;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Media")
public class MediaController {

    private final MediaService mediaService;
    private final JwtUtil jwtUtil;
    private final MediaStorageService mediaStorage;

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

    // Public — the signed token IS the authorization (short-lived, user-bound, cryptographically signed).
    // No Authorization header needed; media players/browsers can play directly via URL.
    @GetMapping("/media/signed")
    @Operation(summary = "Stream signed media file (token-only auth, no Bearer header required)")
    public ResponseEntity<InputStreamResource> streamSigned(@RequestParam String token) {
        JwtUtil.DownloadTokenClaims claims;
        try {
            claims = jwtUtil.extractDownloadClaims(token);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (claims == null || claims.fileUrl() == null || claims.userId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String contentType = URLConnection.guessContentTypeFromName(claims.fileUrl());
        return ResponseEntity.ok()
                .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(mediaStorage.read(claims.fileUrl())));
    }
}
