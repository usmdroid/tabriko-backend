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
import uz.tabriko.dto.response.PlatformSettings;
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
    @Operation(summary = "List all orders (admin) — returns a page; frontend reads .content")
    public ResponseEntity<BaseResponse<?>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(BaseResponse.ok(adminService.getAllOrders(page, size)));
    }

    // --- Users ---

    @GetMapping("/users")
    @Operation(summary = "List CLIENT users with optional search and status filter")
    public ResponseEntity<BaseResponse<?>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(BaseResponse.ok(adminService.getUsers(search, status)));
    }

    @PostMapping("/users/{id}/block")
    @Operation(summary = "Block a user")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<BaseResponse<?>> blockUser(@PathVariable UUID id) {
        adminService.blockUser(id);
        return ResponseEntity.ok(BaseResponse.ok());
    }

    @PostMapping("/users/{id}/unblock")
    @Operation(summary = "Unblock a user")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<BaseResponse<?>> unblockUser(@PathVariable UUID id) {
        adminService.unblockUser(id);
        return ResponseEntity.ok(BaseResponse.ok());
    }

    // --- Stats ---

    @GetMapping("/stats")
    @Operation(summary = "Platform statistics for admin dashboard")
    public ResponseEntity<BaseResponse<?>> getStats() {
        return ResponseEntity.ok(BaseResponse.ok(adminService.getStats()));
    }

    // --- Settings ---

    @GetMapping("/settings")
    @Operation(summary = "Get platform settings")
    public ResponseEntity<BaseResponse<?>> getSettings() {
        return ResponseEntity.ok(BaseResponse.ok(adminService.getSettings()));
    }

    @PatchMapping("/settings")
    @Operation(summary = "Update platform settings")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<BaseResponse<?>> updateSettings(@RequestBody PlatformSettings dto) {
        return ResponseEntity.ok(BaseResponse.ok(adminService.updateSettings(dto)));
    }
}
