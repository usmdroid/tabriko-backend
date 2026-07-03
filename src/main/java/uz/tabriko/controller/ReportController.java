package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.request.CreateReportRequest;
import uz.tabriko.dto.response.ReportResponse;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.ReportService;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @Operation(summary = "Submit a report for a user, content, or order")
    public ResponseEntity<BaseResponse<ReportResponse>> createReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateReportRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(reportService.createReport(principal.getUserId(), req)));
    }
}
