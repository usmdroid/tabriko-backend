package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.domain.entity.Order;
import uz.tabriko.domain.enums.ReportStatus;
import uz.tabriko.dto.request.UpdateReportStatusRequest;
import uz.tabriko.dto.response.PageResponse;
import uz.tabriko.dto.response.ReportResponse;
import uz.tabriko.repository.OrderRepository;
import uz.tabriko.service.ReportService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/moderation")
@RequiredArgsConstructor
@Tag(name = "Moderation")
@PreAuthorize("hasAnyRole('SUPERADMIN', 'MODERATOR')")
public class ModerationController {

    private final OrderRepository orderRepo;
    private final ReportService reportService;

    @PostMapping("/orders/{id}/flag")
    @Transactional
    @Operation(summary = "Flag an order for review")
    public ResponseEntity<BaseResponse<Order>> flagOrder(@PathVariable UUID id) {
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        order.setFlagged(true);
        orderRepo.save(order);
        return ResponseEntity.ok(BaseResponse.ok(order));
    }

    @GetMapping("/reports")
    @Operation(summary = "List all reports (optionally filter by status)")
    public ResponseEntity<BaseResponse<PageResponse<ReportResponse>>> getReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(BaseResponse.ok(reportService.getReports(status, page, size)));
    }

    @PatchMapping("/reports/{id}/status")
    @Operation(summary = "Update report status (RESOLVED or DISMISSED)")
    public ResponseEntity<BaseResponse<ReportResponse>> updateReportStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReportStatusRequest req
    ) {
        return ResponseEntity.ok(BaseResponse.ok(reportService.updateStatus(id, req.getStatus())));
    }
}
