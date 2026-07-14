package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.request.CreateOrderRequest;
import uz.tabriko.dto.request.CreatorRejectOrderRequest;
import uz.tabriko.dto.request.DeliverOrderRequest;
import uz.tabriko.dto.request.RejectOrderRequest;
import uz.tabriko.dto.request.SendMessageRequest;
import uz.tabriko.dto.request.UpdateConsentRequest;
import uz.tabriko.dto.request.UpdatePrivacyRequest;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.OrderService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create a new order")
    public ResponseEntity<BaseResponse<?>> createOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateOrderRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(orderService.createOrder(principal.getUserId(), req)));
    }

    @GetMapping
    @Operation(summary = "Get my orders (client: placed; creator: received)")
    public ResponseEntity<BaseResponse<?>> getMyOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(BaseResponse.ok(
                orderService.getMyOrders(principal.getUserId(), principal.getRole(), page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<BaseResponse<?>> getOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(BaseResponse.ok(orderService.getOrder(principal.getUserId(), id)));
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasRole('CREATOR')")
    @Operation(summary = "Creator delivers an order")
    public ResponseEntity<BaseResponse<?>> deliverOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody DeliverOrderRequest req
    ) {
        return ResponseEntity.ok(BaseResponse.ok(orderService.deliverOrder(principal.getUserId(), id, req)));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Client accepts a delivered order")
    public ResponseEntity<BaseResponse<?>> acceptOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(BaseResponse.ok(orderService.acceptOrder(principal.getUserId(), id)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Client rejects a delivered order")
    public ResponseEntity<BaseResponse<?>> rejectOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RejectOrderRequest req
    ) {
        return ResponseEntity.ok(BaseResponse.ok(orderService.rejectOrder(principal.getUserId(), id, req)));
    }

    @PostMapping("/{id}/creator-accept")
    @PreAuthorize("hasRole('CREATOR')")
    @Operation(summary = "Creator accepts a pending order")
    public ResponseEntity<BaseResponse<?>> creatorAcceptOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(BaseResponse.ok(orderService.creatorAcceptOrder(principal.getUserId(), id)));
    }

    @PostMapping("/{id}/creator-reject")
    @PreAuthorize("hasRole('CREATOR')")
    @Operation(summary = "Creator rejects a pending order")
    public ResponseEntity<BaseResponse<?>> creatorRejectOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CreatorRejectOrderRequest req
    ) {
        return ResponseEntity.ok(BaseResponse.ok(orderService.creatorRejectOrder(principal.getUserId(), id, req)));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Get order chat messages")
    public ResponseEntity<BaseResponse<?>> getOrderMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(BaseResponse.ok(orderService.getOrderMessages(principal.getUserId(), id)));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Send a message in the order chat")
    public ResponseEntity<BaseResponse<?>> sendOrderMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(orderService.sendOrderMessage(principal.getUserId(), id, req)));
    }

    @PostMapping("/{id}/seen")
    @PreAuthorize("hasRole('CREATOR')")
    @Operation(summary = "Creator marks an order as seen")
    public ResponseEntity<BaseResponse<Void>> markSeen(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        orderService.markSeen(principal.getUserId(), id);
        return ResponseEntity.ok(BaseResponse.ok());
    }

    @PatchMapping("/{id}/privacy")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Update order public/private visibility")
    public ResponseEntity<BaseResponse<Void>> updatePrivacy(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePrivacyRequest req
    ) {
        orderService.updatePrivacy(principal.getUserId(), id, req.getIsPublic());
        return ResponseEntity.ok(BaseResponse.ok());
    }

    @PatchMapping("/{id}/consent")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Grant or revoke creator portfolio consent for this order")
    public ResponseEntity<BaseResponse<Void>> updateConsent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConsentRequest req
    ) {
        orderService.updateConsent(principal.getUserId(), id, req.getPortfolioConsent());
        return ResponseEntity.ok(BaseResponse.ok());
    }
}
