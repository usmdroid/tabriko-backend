package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.service.CatalogService;
import uz.tabriko.service.OccasionService;
import uz.tabriko.service.PromotionService;
import uz.tabriko.service.RequisiteService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Catalog")
public class CatalogController {

    private final CatalogService catalogService;
    private final OccasionService occasionService;
    private final PromotionService promotionService;
    private final RequisiteService requisiteService;

    @GetMapping("/categories")
    @Operation(summary = "List all categories")
    public ResponseEntity<BaseResponse<?>> getCategories() {
        return ResponseEntity.ok(BaseResponse.ok(catalogService.getCategories()));
    }

    @GetMapping("/creators")
    @Operation(summary = "List creators with optional filter and search")
    public ResponseEntity<BaseResponse<?>> getCreators(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(BaseResponse.ok(catalogService.getCreators(categoryId, search, page, size)));
    }

    @GetMapping("/creators/top")
    @Operation(summary = "Top 10 creators")
    public ResponseEntity<BaseResponse<?>> getTopCreators() {
        return ResponseEntity.ok(BaseResponse.ok(catalogService.getTopCreators()));
    }

    @GetMapping("/creators/trending")
    @Operation(summary = "Trending creators by recent order volume")
    public ResponseEntity<BaseResponse<?>> getTrendingCreators(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(BaseResponse.ok(catalogService.getTrendingCreators(limit)));
    }

    @GetMapping("/creators/{id}/similar")
    @Operation(summary = "Similar creators by category and tier")
    public ResponseEntity<BaseResponse<?>> getSimilarCreators(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(BaseResponse.ok(catalogService.getSimilarCreators(id, limit)));
    }

    @GetMapping("/creators/for-you")
    @Operation(summary = "Personalized creator suggestions")
    public ResponseEntity<BaseResponse<?>> getForYou(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(BaseResponse.ok(catalogService.getForYouCreators(limit)));
    }

    @GetMapping("/creators/{id}")
    @Operation(summary = "Get creator by ID")
    public ResponseEntity<BaseResponse<?>> getCreator(@PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ok(catalogService.getCreator(id)));
    }

    @GetMapping("/creators/by-code/{code}")
    @Operation(summary = "Get creator by public code")
    public ResponseEntity<BaseResponse<?>> getCreatorByCode(@PathVariable String code) {
        return ResponseEntity.ok(BaseResponse.ok(catalogService.getCreatorByCode(code.toLowerCase())));
    }

    @GetMapping("/catalog/occasions/upcoming")
    @Operation(summary = "Upcoming occasions for the home screen carousel")
    public ResponseEntity<BaseResponse<?>> getUpcomingOccasions(
            @RequestParam(defaultValue = "6") int limit
    ) {
        return ResponseEntity.ok(BaseResponse.ok(occasionService.getUpcoming(limit)));
    }

    @GetMapping("/catalog/promotions")
    @Operation(summary = "Active promotions for the home screen carousel")
    public ResponseEntity<BaseResponse<?>> getPromotions() {
        return ResponseEntity.ok(BaseResponse.ok(promotionService.getActive()));
    }

    @GetMapping("/catalog/requisites")
    @Operation(summary = "Active requisite catalog items")
    public ResponseEntity<BaseResponse<?>> getRequisites() {
        return ResponseEntity.ok(BaseResponse.ok(requisiteService.getActiveRequisites()));
    }
}
