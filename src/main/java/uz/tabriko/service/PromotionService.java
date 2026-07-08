package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.Promotion;
import uz.tabriko.dto.request.AdminPromotionRequest;
import uz.tabriko.dto.response.AdminPromotionResponse;
import uz.tabriko.dto.response.PromotionResponse;
import uz.tabriko.repository.CategoryRepository;
import uz.tabriko.repository.PromotionRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepo;
    private final CategoryRepository categoryRepo;

    // --- Public ---

    public List<PromotionResponse> getActive() {
        return promotionRepo.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private PromotionResponse toResponse(Promotion p) {
        PromotionResponse r = new PromotionResponse();
        r.setId(p.getId());
        r.setTitle(p.getTitle());
        r.setSubtitle(p.getSubtitle());
        r.setImageUrl(p.getImageUrl());
        r.setColor(p.getColor());
        r.setCategoryId(p.getCategoryId());
        r.setExternalUrl(p.getExternalUrl());
        return r;
    }

    // --- Admin ---

    public List<AdminPromotionResponse> getAdminPromotions() {
        return promotionRepo.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AdminPromotionResponse createPromotion(AdminPromotionRequest req) {
        Promotion p = new Promotion();
        applyRequest(p, req);
        return toAdminResponse(promotionRepo.save(p));
    }

    @Transactional
    public AdminPromotionResponse updatePromotion(Long id, AdminPromotionRequest req) {
        Promotion p = promotionRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Promotion not found"));
        applyRequest(p, req);
        return toAdminResponse(promotionRepo.save(p));
    }

    @Transactional
    public void deletePromotion(Long id) {
        Promotion p = promotionRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Promotion not found"));
        promotionRepo.delete(p);
    }

    private void applyRequest(Promotion p, AdminPromotionRequest req) {
        if (req.getCategoryId() != null && !categoryRepo.existsById(req.getCategoryId())) {
            throw ApiException.badRequest("Category not found");
        }
        p.setTitle(req.getTitle());
        p.setSubtitle(req.getSubtitle());
        p.setImageUrl(req.getImageUrl());
        p.setColor(req.getColor());
        p.setCategoryId(req.getCategoryId());
        p.setExternalUrl(req.getExternalUrl());
        p.setActive(req.isActive());
        p.setSortOrder(req.getSortOrder());
    }

    private AdminPromotionResponse toAdminResponse(Promotion p) {
        AdminPromotionResponse r = new AdminPromotionResponse();
        r.setId(p.getId());
        r.setTitle(p.getTitle());
        r.setSubtitle(p.getSubtitle());
        r.setImageUrl(p.getImageUrl());
        r.setColor(p.getColor());
        r.setCategoryId(p.getCategoryId());
        r.setExternalUrl(p.getExternalUrl());
        r.setActive(p.isActive());
        r.setSortOrder(p.getSortOrder());
        r.setCreatedAt(p.getCreatedAt());
        return r;
    }
}
