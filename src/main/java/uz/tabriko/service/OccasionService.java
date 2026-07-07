package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.Occasion;
import uz.tabriko.dto.request.AdminOccasionRequest;
import uz.tabriko.dto.response.AdminOccasionResponse;
import uz.tabriko.dto.response.OccasionResponse;
import uz.tabriko.repository.CategoryRepository;
import uz.tabriko.repository.OccasionRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OccasionService {

    private final OccasionRepository occasionRepo;
    private final CategoryRepository categoryRepo;

    // --- Public ---

    public List<OccasionResponse> getUpcoming(int limit) {
        LocalDate today = LocalDate.now();
        return occasionRepo.findByActiveTrue().stream()
                .map(o -> toUpcomingResponse(o, today))
                .filter(r -> r != null)
                .sorted(Comparator.comparingInt(OccasionResponse::getDaysLeft))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private OccasionResponse toUpcomingResponse(Occasion o, LocalDate today) {
        LocalDate nextDate;
        if (o.isRecurringYearly()) {
            nextDate = o.getEventDate().withYear(today.getYear());
            if (nextDate.isBefore(today)) {
                nextDate = nextDate.withYear(today.getYear() + 1);
            }
        } else {
            if (o.getEventDate().isBefore(today)) {
                return null;
            }
            nextDate = o.getEventDate();
        }
        OccasionResponse r = new OccasionResponse();
        r.setId(o.getId());
        r.setTitle(o.getTitle());
        r.setDate(nextDate);
        r.setDaysLeft((int) ChronoUnit.DAYS.between(today, nextDate));
        r.setEmoji(o.getEmoji());
        r.setColor(o.getColor());
        r.setImageUrl(o.getImageUrl());
        r.setCategoryId(o.getCategoryId());
        return r;
    }

    // --- Admin ---

    public List<AdminOccasionResponse> getAdminOccasions() {
        return occasionRepo.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AdminOccasionResponse createOccasion(AdminOccasionRequest req) {
        Occasion o = new Occasion();
        applyRequest(o, req);
        return toAdminResponse(occasionRepo.save(o));
    }

    @Transactional
    public AdminOccasionResponse updateOccasion(Long id, AdminOccasionRequest req) {
        Occasion o = occasionRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Occasion not found"));
        applyRequest(o, req);
        return toAdminResponse(occasionRepo.save(o));
    }

    @Transactional
    public void deleteOccasion(Long id) {
        Occasion o = occasionRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Occasion not found"));
        occasionRepo.delete(o);
    }

    private void applyRequest(Occasion o, AdminOccasionRequest req) {
        if (req.getCategoryId() != null && !categoryRepo.existsById(req.getCategoryId())) {
            throw ApiException.badRequest("Category not found");
        }
        o.setTitle(req.getTitle());
        o.setEventDate(req.getEventDate());
        o.setRecurringYearly(req.isRecurringYearly());
        o.setEmoji(req.getEmoji());
        o.setColor(req.getColor());
        o.setImageUrl(req.getImageUrl());
        o.setCategoryId(req.getCategoryId());
        o.setActive(req.isActive());
        o.setSortOrder(req.getSortOrder());
    }

    private AdminOccasionResponse toAdminResponse(Occasion o) {
        AdminOccasionResponse r = new AdminOccasionResponse();
        r.setId(o.getId());
        r.setTitle(o.getTitle());
        r.setEventDate(o.getEventDate());
        r.setRecurringYearly(o.isRecurringYearly());
        r.setEmoji(o.getEmoji());
        r.setColor(o.getColor());
        r.setImageUrl(o.getImageUrl());
        r.setCategoryId(o.getCategoryId());
        r.setActive(o.isActive());
        r.setSortOrder(o.getSortOrder());
        r.setCreatedAt(o.getCreatedAt());
        return r;
    }
}
