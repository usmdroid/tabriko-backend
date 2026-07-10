package uz.tabriko.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.PortfolioItem;
import uz.tabriko.domain.entity.User;
import uz.tabriko.dto.response.CreatorResponse;
import uz.tabriko.repository.CategoryRepository;
import uz.tabriko.repository.CreatorProfileRepository;
import uz.tabriko.repository.PortfolioItemRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Regression coverage for the N+1 fix: creator listing endpoints must fetch each creator's
// portfolio in a single batched query keyed by creator id, never one query per creator.
@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock CategoryRepository categoryRepo;
    @Mock CreatorProfileRepository creatorProfileRepo;
    @Mock PortfolioItemRepository portfolioRepo;
    @Mock UserMapper mapper;

    @InjectMocks CatalogService catalogService;

    private CreatorProfile profile(UUID userId) {
        User user = new User();
        user.setId(userId);
        CreatorProfile cp = new CreatorProfile();
        cp.setUser(user);
        // @MapsId only derives the id at persist time; outside JPA it must be set explicitly.
        cp.setUserId(userId);
        return cp;
    }

    private PortfolioItem portfolioItemFor(UUID creatorId) {
        User creator = new User();
        creator.setId(creatorId);
        PortfolioItem item = new PortfolioItem();
        item.setCreator(creator);
        return item;
    }

    @Test
    void getCreators_batchFetchesPortfolioForAllCreatorsInOneQuery() {
        UUID creator1 = UUID.randomUUID();
        UUID creator2 = UUID.randomUUID();
        Page<CreatorProfile> page = new PageImpl<>(List.of(profile(creator1), profile(creator2)));
        when(creatorProfileRepo.findAllFiltered(any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(portfolioRepo.findPublicWithConsentByCreatorIds(anyList()))
                .thenReturn(List.of(portfolioItemFor(creator1), portfolioItemFor(creator2)));
        when(mapper.toCreatorResponse(any(), anyList())).thenReturn(new CreatorResponse());

        catalogService.getCreators(null, null, 0, 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(portfolioRepo, times(1)).findPublicWithConsentByCreatorIds(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(creator1, creator2);
        // The per-creator query must never be used for a list endpoint.
        verify(portfolioRepo, never()).findPublicWithConsent(any());
    }

    @Test
    void getCreators_emptyResult_skipsPortfolioQueryEntirely() {
        when(creatorProfileRepo.findAllFiltered(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        catalogService.getCreators(null, null, 0, 10);

        verify(portfolioRepo, never()).findPublicWithConsentByCreatorIds(anyList());
    }

    @Test
    void getTopCreators_batchFetchesPortfolioInOneQuery() {
        UUID creator1 = UUID.randomUUID();
        UUID creator2 = UUID.randomUUID();
        when(creatorProfileRepo.findTop10()).thenReturn(List.of(profile(creator1), profile(creator2)));
        when(portfolioRepo.findPublicWithConsentByCreatorIds(anyList()))
                .thenReturn(List.of(portfolioItemFor(creator1)));
        when(mapper.toCreatorResponse(any(), anyList())).thenReturn(new CreatorResponse());

        catalogService.getTopCreators();

        verify(portfolioRepo, times(1)).findPublicWithConsentByCreatorIds(anyList());
        verify(portfolioRepo, never()).findPublicWithConsent(any());
    }

    @Test
    void getForYouCreators_batchFetchesPortfolioInOneQuery() {
        UUID creator1 = UUID.randomUUID();
        when(creatorProfileRepo.findForYou(any(Pageable.class))).thenReturn(List.of(profile(creator1)));
        when(portfolioRepo.findPublicWithConsentByCreatorIds(anyList()))
                .thenReturn(List.of(portfolioItemFor(creator1)));
        when(mapper.toCreatorResponse(any(), anyList())).thenReturn(new CreatorResponse());

        catalogService.getForYouCreators(5);

        verify(creatorProfileRepo).findForYou(PageRequest.of(0, 5));
        verify(portfolioRepo, times(1)).findPublicWithConsentByCreatorIds(List.of(creator1));
        verify(portfolioRepo, never()).findPublicWithConsent(any());
    }

    @Test
    void getCreator_singleCreator_usesSingularPortfolioQuery() {
        UUID creatorId = UUID.randomUUID();
        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(java.util.Optional.of(profile(creatorId)));
        when(portfolioRepo.findPublicWithConsent(creatorId)).thenReturn(List.of(portfolioItemFor(creatorId)));

        catalogService.getCreator(creatorId);

        verify(portfolioRepo).findPublicWithConsent(creatorId);
        verify(portfolioRepo, never()).findPublicWithConsentByCreatorIds(anyList());
    }
}
