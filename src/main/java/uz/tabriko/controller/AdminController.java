package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.request.AddCreatorRequest;
import uz.tabriko.service.AdminService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin")
@PreAuthorize("hasAnyRole('SUPERADMIN', 'MODERATOR')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/creators")
    @Operation(summary = "List all creators (admin)")
    public ResponseEntity<BaseResponse<?>> listCreators() {
        return ResponseEntity.ok(BaseResponse.ok(adminService.getAllCreators()));
    }

    @PostMapping("/creators")
    @Operation(summary = "Add a new creator")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<BaseResponse<?>> addCreator(@Valid @RequestBody AddCreatorRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(adminService.addCreator(req)));
    }

    @PostMapping("/creators/{id}/verify")
    @Operation(summary = "Verify a creator")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'MODERATOR')")
    public ResponseEntity<BaseResponse<?>> verifyCreator(@PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ok(adminService.verifyCreator(id)));
    }

    @GetMapping("/orders")
    @Operation(summary = "List all orders (admin)")
    public ResponseEntity<BaseResponse<?>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(BaseResponse.ok(adminService.getAllOrders(page, size)));
    }
}
