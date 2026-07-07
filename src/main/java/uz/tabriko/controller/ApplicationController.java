package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.request.ReplyApplicationRequest;
import uz.tabriko.dto.request.SubmitApplicationRequest;
import uz.tabriko.service.ApplicationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Tag(name = "Applications (public)")
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    @Operation(summary = "Submit a creator application (OTP-verified, no JWT)")
    public ResponseEntity<BaseResponse<?>> submit(@Valid @RequestBody SubmitApplicationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(applicationService.submit(req)));
    }

    @PostMapping(value = "/upload-sample", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a sample video for a creator application (public, size/type limited)")
    public ResponseEntity<BaseResponse<?>> uploadSample(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(applicationService.uploadSample(file)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get application status and thread by tracking token")
    public ResponseEntity<BaseResponse<?>> get(
            @PathVariable UUID id,
            @RequestParam String token
    ) {
        return ResponseEntity.ok(BaseResponse.ok(applicationService.getByToken(id, token)));
    }

    @PostMapping("/{id}/reply")
    @Operation(summary = "Applicant reply (only when status is INFO_REQUESTED)")
    public ResponseEntity<BaseResponse<?>> reply(
            @PathVariable UUID id,
            @RequestParam String token,
            @Valid @RequestBody ReplyApplicationRequest req
    ) {
        applicationService.replyAsApplicant(id, token, req);
        return ResponseEntity.ok(BaseResponse.ok());
    }
}
