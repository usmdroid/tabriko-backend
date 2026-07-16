package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.CreatorRequisite;
import uz.tabriko.domain.entity.CreatorServiceOffering;
import uz.tabriko.domain.entity.PortfolioItem;
import uz.tabriko.dto.response.CategoryResponse;
import uz.tabriko.dto.response.CreatorResponse;
import uz.tabriko.dto.response.PageResponse;
import uz.tabriko.dto.response.RequisiteItemResponse;
import uz.tabriko.repository.CategoryRepository;
import uz.tabriko.repository.CreatorProfileRepository;
import uz.tabriko.repository.CreatorRequisiteRepository;
import uz.tabriko.repository.CreatorServiceOfferingRepository;
import uz.tabriko.repository.PortfolioItemRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CategoryRepository categoryRepo;
    private final CreatorProfileRepository creatorProfileRepo;
    private final PortfolioItemRepository portfolioRepo;
    private final CreatorServiceOfferingRepository serviceOfferingRepo;
    private final CreatorRequisiteRepository creatorRequisiteRepo;
    private final UserMapper mapper;

    public List<CategoryResponse> getCategories() {
        return categoryRepo.findByArchivedFalse().stream()
                .map(mapper::toCategoryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<CreatorResponse> getCreators(Long categoryId, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        String searchPattern = normalizedSearch == null ? null : "%" + normalizedSearch.toLowerCase() + "%";
        Page<CreatorProfile> profiles = creatorProfileRepo.findAllFiltered(categoryId, normalizedSearch, searchPattern, pageable);
        Map<UUID, List<PortfolioItem>> portfolioByCreator = groupPortfolioByCreator(profiles.getContent());
        Map<UUID, List<CreatorServiceOffering>> servicesByCreator = groupServicesByCreator(profiles.getContent());
        return PageResponse.of(profiles, cp -> mapper.toCreatorResponse(
                cp, portfolioByCreator.getOrDefault(cp.getUserId(), List.of()),
                servicesByCreator.getOrDefault(cp.getUserId(), List.of())));
    }

    @Transactional(readOnly = true)
    public List<CreatorResponse> getTopCreators() {
        List<CreatorProfile> creators = creatorProfileRepo.findTop10();
        Map<UUID, List<PortfolioItem>> portfolioByCreator = groupPortfolioByCreator(creators);
        Map<UUID, List<CreatorServiceOffering>> servicesByCreator = groupServicesByCreator(creators);
        return creators.stream()
                .map(cp -> mapper.toCreatorResponse(cp, portfolioByCreator.getOrDefault(cp.getUserId(), List.of()),
                        servicesByCreator.getOrDefault(cp.getUserId(), List.of())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CreatorResponse> getForYouCreators(int limit) {
        List<CreatorProfile> creators = creatorProfileRepo.findForYou(PageRequest.of(0, limit));
        Map<UUID, List<PortfolioItem>> portfolioByCreator = groupPortfolioByCreator(creators);
        Map<UUID, List<CreatorServiceOffering>> servicesByCreator = groupServicesByCreator(creators);
        return creators.stream()
                .map(cp -> mapper.toCreatorResponse(cp, portfolioByCreator.getOrDefault(cp.getUserId(), List.of()),
                        servicesByCreator.getOrDefault(cp.getUserId(), List.of())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CreatorResponse> getTrendingCreators(int limit) {
        Instant cutoff = Instant.now().minus(14, ChronoUnit.DAYS);
        List<CreatorProfile> creators = creatorProfileRepo.findTrending(cutoff, PageRequest.of(0, limit)).getContent();
        Map<UUID, List<PortfolioItem>> portfolioByCreator = groupPortfolioByCreator(creators);
        Map<UUID, List<CreatorServiceOffering>> servicesByCreator = groupServicesByCreator(creators);
        return creators.stream()
                .map(cp -> mapper.toCreatorResponse(cp, portfolioByCreator.getOrDefault(cp.getUserId(), List.of()),
                        servicesByCreator.getOrDefault(cp.getUserId(), List.of())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CreatorResponse> getSimilarCreators(UUID id, int limit) {
        CreatorProfile ref = creatorProfileRepo.findByUserId(id)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        List<CreatorProfile> creators = creatorProfileRepo.findSimilar(
                id, ref.getCategory(), ref.getTier(), PageRequest.of(0, limit));
        Map<UUID, List<PortfolioItem>> portfolioByCreator = groupPortfolioByCreator(creators);
        Map<UUID, List<CreatorServiceOffering>> servicesByCreator = groupServicesByCreator(creators);
        return creators.stream()
                .map(cp -> mapper.toCreatorResponse(cp, portfolioByCreator.getOrDefault(cp.getUserId(), List.of()),
                        servicesByCreator.getOrDefault(cp.getUserId(), List.of())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CreatorResponse getCreator(UUID creatorId) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        return buildFullCreatorResponse(cp);
    }

    @Transactional(readOnly = true)
    public CreatorResponse getCreatorByCode(String code) {
        CreatorProfile cp = creatorProfileRepo.findByPublicCode(code)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        return buildFullCreatorResponse(cp);
    }

    private CreatorResponse buildFullCreatorResponse(CreatorProfile cp) {
        UUID creatorId = cp.getUserId();
        List<PortfolioItem> portfolio = portfolioRepo.findPublicWithConsent(creatorId);
        List<CreatorServiceOffering> services = serviceOfferingRepo.findByCreator_Id(creatorId);
        CreatorResponse r = mapper.toCreatorResponse(cp, portfolio, services);
        List<CreatorRequisite> requisites = creatorRequisiteRepo.findByCreatorUserIdOrderByCreatedAtAsc(creatorId);
        r.setRequisites(requisites.stream().map(cr -> {
            RequisiteItemResponse ri = new RequisiteItemResponse();
            ri.setId(cr.getId());
            ri.setName(cr.getName());
            ri.setEmoji(cr.getEmoji());
            ri.setPrice(cr.getPrice());
            return ri;
        }).collect(Collectors.toList()));
        return r;
    }

    private Map<UUID, List<PortfolioItem>> groupPortfolioByCreator(List<CreatorProfile> creators) {
        if (creators.isEmpty()) {
            return Map.of();
        }
        List<UUID> creatorIds = creators.stream().map(CreatorProfile::getUserId).collect(Collectors.toList());
        return portfolioRepo.findPublicWithConsentByCreatorIds(creatorIds).stream()
                .collect(Collectors.groupingBy(p -> p.getCreator().getId()));
    }

    private Map<UUID, List<CreatorServiceOffering>> groupServicesByCreator(List<CreatorProfile> creators) {
        if (creators.isEmpty()) {
            return Map.of();
        }
        List<UUID> creatorIds = creators.stream().map(CreatorProfile::getUserId).collect(Collectors.toList());
        return serviceOfferingRepo.findByCreator_IdIn(creatorIds).stream()
                .collect(Collectors.groupingBy(s -> s.getCreator().getId()));
    }
}
