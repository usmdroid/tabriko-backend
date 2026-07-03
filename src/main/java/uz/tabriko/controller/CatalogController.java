package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.service.CatalogService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Catalog")
public class CatalogController {

    private final CatalogService catalogService;

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
}
