package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.PortfolioItem;
import uz.tabriko.dto.response.CategoryResponse;
import uz.tabriko.dto.response.CreatorResponse;
import uz.tabriko.dto.response.PageResponse;
import uz.tabriko.repository.CategoryRepository;
import uz.tabriko.repository.CreatorProfileRepository;
import uz.tabriko.repository.PortfolioItemRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CategoryRepository categoryRepo;
    private final CreatorProfileRepository creatorProfileRepo;
    private final PortfolioItemRepository portfolioRepo;
    private final UserMapper mapper;

    public List<CategoryResponse> getCategories() {
        return categoryRepo.findAll().stream()
                .map(mapper::toCategoryResponse)
                .collect(Collectors.toList());
    }

    public PageResponse<CreatorResponse> getCreators(Long categoryId, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        String searchPattern = normalizedSearch == null ? null : "%" + normalizedSearch.toLowerCase() + "%";
        Page<CreatorProfile> profiles = creatorProfileRepo.findAllFiltered(categoryId, normalizedSearch, searchPattern, pageable);
        return PageResponse.of(profiles, cp -> {
            List<PortfolioItem> portfolio = portfolioRepo.findPublicWithConsent(cp.getUserId());
            return mapper.toCreatorResponse(cp, portfolio);
        });
    }

    public List<CreatorResponse> getTopCreators() {
        return creatorProfileRepo.findTop10().stream()
                .map(cp -> {
                    List<PortfolioItem> portfolio = portfolioRepo.findPublicWithConsent(cp.getUserId());
                    return mapper.toCreatorResponse(cp, portfolio);
                })
                .collect(Collectors.toList());
    }

    public List<CreatorResponse> getForYouCreators(int limit) {
        return creatorProfileRepo.findForYou(PageRequest.of(0, limit)).stream()
                .map(cp -> {
                    List<PortfolioItem> portfolio = portfolioRepo.findPublicWithConsent(cp.getUserId());
                    return mapper.toCreatorResponse(cp, portfolio);
                })
                .collect(Collectors.toList());
    }

    public CreatorResponse getCreator(UUID creatorId) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        List<PortfolioItem> portfolio = portfolioRepo.findPublicWithConsent(creatorId);
        return mapper.toCreatorResponse(cp, portfolio);
    }
}
