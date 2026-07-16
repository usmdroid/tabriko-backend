package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.request.DiagnosticsReportRequest;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.DiagnosticsService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diagnostics")
@RequiredArgsConstructor
@Tag(name = "Diagnostics")
public class DiagnosticsController {

    private final DiagnosticsService diagnosticsService;

    @PostMapping("/report")
    @Operation(summary = "Report a crash or error (auth optional)")
    public ResponseEntity<BaseResponse<Void>> report(
            @Valid @RequestBody DiagnosticsReportRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal != null ? principal.getUserId() : null;
        diagnosticsService.report(req, userId);
        return ResponseEntity.ok(BaseResponse.ok());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.ok(BaseResponse.ok());
    }
}
