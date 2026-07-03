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
import uz.tabriko.dto.request.CreateReviewRequest;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.ReviewService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/orders/{orderId}/review")
    @Operation(summary = "Submit a review for a completed order")
    public ResponseEntity<BaseResponse<?>> createReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateReviewRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created(reviewService.createReview(principal.getUserId(), orderId, req)));
    }

    @GetMapping("/creators/{creatorId}/reviews")
    @Operation(summary = "Get reviews for a creator")
    public ResponseEntity<BaseResponse<?>> getCreatorReviews(
            @PathVariable UUID creatorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(BaseResponse.ok(reviewService.getCreatorReviews(creatorId, page, size)));
    }
}
