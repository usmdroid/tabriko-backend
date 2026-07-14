package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.CreatorRequisite;
import uz.tabriko.domain.entity.RequisiteCatalog;
import uz.tabriko.domain.enums.OrderType;
import uz.tabriko.domain.enums.RequisiteSource;
import uz.tabriko.dto.request.AddCreatorRequisiteRequest;
import uz.tabriko.dto.request.AdminRequisiteRequest;
import uz.tabriko.dto.response.AdminRequisiteResponse;
import uz.tabriko.dto.response.CreatorRequisiteResponse;
import uz.tabriko.dto.response.RequisiteItemResponse;
import uz.tabriko.repository.CreatorRequisiteRepository;
import uz.tabriko.repository.RequisiteCatalogRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequisiteServiceTest {

    @Mock RequisiteCatalogRepository catalogRepo;
    @Mock CreatorRequisiteRepository creatorRequisiteRepo;

    @InjectMocks RequisiteService requisiteService;

    private UUID creatorId;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
    }

    // ===== Per-type list =====

    @Test
    void getCreatorRequisites_returnsOnlyRequestedType() {
        CreatorRequisite video = requisite(1L, creatorId, "Gul", "🌸", RequisiteSource.CATALOG, OrderType.VIDEO);
        when(creatorRequisiteRepo.findByCreatorUserIdAndServiceTypeOrderByCreatedAtAsc(creatorId, OrderType.VIDEO))
                .thenReturn(List.of(video));

        List<CreatorRequisiteResponse> result = requisiteService.getCreatorRequisites(creatorId, OrderType.VIDEO);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getServiceType()).isEqualTo(OrderType.VIDEO);
        verify(creatorRequisiteRepo).findByCreatorUserIdAndServiceTypeOrderByCreatedAtAsc(creatorId, OrderType.VIDEO);
    }

    @Test
    void getCreatorRequisites_audioTypeQueriesAudio() {
        when(creatorRequisiteRepo.findByCreatorUserIdAndServiceTypeOrderByCreatedAtAsc(creatorId, OrderType.AUDIO))
                .thenReturn(List.of());

        List<CreatorRequisiteResponse> result = requisiteService.getCreatorRequisites(creatorId, OrderType.AUDIO);

        assertThat(result).isEmpty();
        verify(creatorRequisiteRepo).findByCreatorUserIdAndServiceTypeOrderByCreatedAtAsc(creatorId, OrderType.AUDIO);
    }

    // ===== Per-type add (happy path) =====

    @Test
    void addCreatorRequisite_fromCatalog_setsServiceType() {
        RequisiteCatalog catalog = catalog(1L, "Gul", "🌸");
        when(catalogRepo.findById(1L)).thenReturn(Optional.of(catalog));
        when(creatorRequisiteRepo.countByCreatorUserIdAndServiceType(creatorId, OrderType.VIDEO)).thenReturn(0L);
        when(creatorRequisiteRepo.existsByCreatorUserIdAndServiceTypeAndNameIgnoreCase(
                creatorId, OrderType.VIDEO, "Gul")).thenReturn(false);

        CreatorRequisite saved = requisite(10L, creatorId, "Gul", "🌸", RequisiteSource.CATALOG, OrderType.VIDEO);
        when(creatorRequisiteRepo.save(any())).thenReturn(saved);

        AddCreatorRequisiteRequest req = videoReq();
        req.setCatalogId(1L);

        CreatorRequisiteResponse resp = requisiteService.addCreatorRequisite(creatorId, req);

        assertThat(resp.getServiceType()).isEqualTo(OrderType.VIDEO);
        assertThat(resp.getName()).isEqualTo("Gul");

        ArgumentCaptor<CreatorRequisite> captor = ArgumentCaptor.forClass(CreatorRequisite.class);
        verify(creatorRequisiteRepo).save(captor.capture());
        assertThat(captor.getValue().getServiceType()).isEqualTo(OrderType.VIDEO);
    }

    @Test
    void addCreatorRequisite_customName_setsServiceType() {
        when(creatorRequisiteRepo.countByCreatorUserIdAndServiceType(creatorId, OrderType.AUDIO)).thenReturn(0L);
        when(creatorRequisiteRepo.existsByCreatorUserIdAndServiceTypeAndNameIgnoreCase(
                creatorId, OrderType.AUDIO, "Sham")).thenReturn(false);

        CreatorRequisite saved = requisite(11L, creatorId, "Sham", null, RequisiteSource.CUSTOM, OrderType.AUDIO);
        when(creatorRequisiteRepo.save(any())).thenReturn(saved);

        AddCreatorRequisiteRequest req = audioReq();
        req.setCustomName("Sham");

        CreatorRequisiteResponse resp = requisiteService.addCreatorRequisite(creatorId, req);

        assertThat(resp.getServiceType()).isEqualTo(OrderType.AUDIO);
        ArgumentCaptor<CreatorRequisite> captor = ArgumentCaptor.forClass(CreatorRequisite.class);
        verify(creatorRequisiteRepo).save(captor.capture());
        assertThat(captor.getValue().getServiceType()).isEqualTo(OrderType.AUDIO);
    }

    // ===== Per-type uniqueness check =====

    @Test
    void addCreatorRequisite_duplicateWithinSameType_throwsConflict() {
        RequisiteCatalog catalog = catalog(1L, "Gul", "🌸");
        when(catalogRepo.findById(1L)).thenReturn(Optional.of(catalog));
        when(creatorRequisiteRepo.countByCreatorUserIdAndServiceType(creatorId, OrderType.VIDEO)).thenReturn(2L);
        when(creatorRequisiteRepo.existsByCreatorUserIdAndServiceTypeAndNameIgnoreCase(
                creatorId, OrderType.VIDEO, "Gul")).thenReturn(true);

        AddCreatorRequisiteRequest req = videoReq();
        req.setCatalogId(1L);

        assertThatThrownBy(() -> requisiteService.addCreatorRequisite(creatorId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void addCreatorRequisite_sameNameDifferentType_allowed() {
        // "Gul" exists for VIDEO — adding same name for AUDIO should be checked against AUDIO only
        when(creatorRequisiteRepo.countByCreatorUserIdAndServiceType(creatorId, OrderType.AUDIO)).thenReturn(1L);
        when(creatorRequisiteRepo.existsByCreatorUserIdAndServiceTypeAndNameIgnoreCase(
                creatorId, OrderType.AUDIO, "Gul")).thenReturn(false);

        CreatorRequisite saved = requisite(20L, creatorId, "Gul", null, RequisiteSource.CUSTOM, OrderType.AUDIO);
        when(creatorRequisiteRepo.save(any())).thenReturn(saved);

        AddCreatorRequisiteRequest req = audioReq();
        req.setCustomName("Gul");

        CreatorRequisiteResponse resp = requisiteService.addCreatorRequisite(creatorId, req);

        assertThat(resp.getServiceType()).isEqualTo(OrderType.AUDIO);
        // Must NOT check VIDEO uniqueness
        verify(creatorRequisiteRepo, never()).existsByCreatorUserIdAndServiceTypeAndNameIgnoreCase(
                eq(creatorId), eq(OrderType.VIDEO), any());
    }

    // ===== Per-type cap enforcement =====

    @Test
    void addCreatorRequisite_perTypeCap_throwsBadRequest() {
        when(creatorRequisiteRepo.countByCreatorUserIdAndServiceType(creatorId, OrderType.VIDEO)).thenReturn(20L);

        AddCreatorRequisiteRequest req = videoReq();
        req.setCustomName("NewItem");

        assertThatThrownBy(() -> requisiteService.addCreatorRequisite(creatorId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Maximum");
    }

    @Test
    void addCreatorRequisite_audioAtCapButVideoUnder_videoStillAllowed() {
        // AUDIO at cap — adding to VIDEO should not be blocked by AUDIO count
        when(creatorRequisiteRepo.countByCreatorUserIdAndServiceType(creatorId, OrderType.VIDEO)).thenReturn(5L);
        when(creatorRequisiteRepo.existsByCreatorUserIdAndServiceTypeAndNameIgnoreCase(
                creatorId, OrderType.VIDEO, "Gul")).thenReturn(false);
        when(creatorRequisiteRepo.save(any())).thenReturn(
                requisite(30L, creatorId, "Gul", null, RequisiteSource.CUSTOM, OrderType.VIDEO));

        AddCreatorRequisiteRequest req = videoReq();
        req.setCustomName("Gul");

        assertThatCode(() -> requisiteService.addCreatorRequisite(creatorId, req))
                .doesNotThrowAnyException();
    }

    // ===== Delete own requisite =====

    @Test
    void deleteCreatorRequisite_ownRequisite_deletesSuccessfully() {
        CreatorRequisite cr = requisite(99L, creatorId, "Gul", "🌸", RequisiteSource.CATALOG, OrderType.VIDEO);
        when(creatorRequisiteRepo.findByIdAndCreatorUserId(99L, creatorId)).thenReturn(Optional.of(cr));

        requisiteService.deleteCreatorRequisite(creatorId, 99L);

        verify(creatorRequisiteRepo).delete(cr);
    }

    @Test
    void deleteCreatorRequisite_notOwned_throwsForbidden() {
        when(creatorRequisiteRepo.findByIdAndCreatorUserId(99L, creatorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requisiteService.deleteCreatorRequisite(creatorId, 99L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not owned");
    }

    // ===== findByCreatorId returns all types =====

    @Test
    void findByCreatorId_returnsAllTypesForPublicProfile() {
        CreatorRequisite video = requisite(1L, creatorId, "Gul", "🌸", RequisiteSource.CATALOG, OrderType.VIDEO);
        CreatorRequisite audio = requisite(2L, creatorId, "Sham", null, RequisiteSource.CUSTOM, OrderType.AUDIO);
        when(creatorRequisiteRepo.findByCreatorUserIdOrderByCreatedAtAsc(creatorId))
                .thenReturn(List.of(video, audio));

        List<CreatorRequisite> result = requisiteService.findByCreatorId(creatorId);

        assertThat(result).hasSize(2);
    }

    // ===== Active catalog endpoint =====

    @Test
    void getActiveRequisites_returnsOnlyActiveItems() {
        RequisiteCatalog active = catalog(1L, "Gul", "🌸");
        when(catalogRepo.findByActiveTrue()).thenReturn(List.of(active));

        List<RequisiteItemResponse> result = requisiteService.getActiveRequisites();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Gul");
    }

    // ===== Admin create =====

    @Test
    void createRequisite_savesAndReturns() {
        AdminRequisiteRequest req = new AdminRequisiteRequest();
        req.setName("Sham");
        req.setEmoji("🕯️");

        RequisiteCatalog saved = new RequisiteCatalog();
        saved.setId(10L);
        saved.setName("Sham");
        saved.setEmoji("🕯️");
        saved.setActive(true);
        saved.setCreatedAt(Instant.now());
        when(catalogRepo.save(any())).thenReturn(saved);

        AdminRequisiteResponse resp = requisiteService.createRequisite(req);

        assertThat(resp.getName()).isEqualTo("Sham");
        assertThat(resp.isActive()).isTrue();
    }

    // ===== Validation: exactly one of catalogId/customName =====

    @Test
    void addCreatorRequisite_bothProvided_throwsBadRequest() {
        AddCreatorRequisiteRequest req = videoReq();
        req.setCatalogId(1L);
        req.setCustomName("Custom");

        assertThatThrownBy(() -> requisiteService.addCreatorRequisite(creatorId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Exactly one");
    }

    @Test
    void addCreatorRequisite_neitherProvided_throwsBadRequest() {
        AddCreatorRequisiteRequest req = videoReq();

        assertThatThrownBy(() -> requisiteService.addCreatorRequisite(creatorId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Exactly one");
    }

    @Test
    void addCreatorRequisite_inactiveCatalogItem_throwsBadRequest() {
        RequisiteCatalog catalog = catalog(1L, "Gul", "🌸");
        catalog.setActive(false);

        when(catalogRepo.findById(1L)).thenReturn(Optional.of(catalog));
        when(creatorRequisiteRepo.countByCreatorUserIdAndServiceType(creatorId, OrderType.VIDEO)).thenReturn(0L);

        AddCreatorRequisiteRequest req = videoReq();
        req.setCatalogId(1L);

        assertThatThrownBy(() -> requisiteService.addCreatorRequisite(creatorId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not active");
    }

    // --- helpers ---

    private AddCreatorRequisiteRequest videoReq() {
        AddCreatorRequisiteRequest req = new AddCreatorRequisiteRequest();
        req.setServiceType(OrderType.VIDEO);
        return req;
    }

    private AddCreatorRequisiteRequest audioReq() {
        AddCreatorRequisiteRequest req = new AddCreatorRequisiteRequest();
        req.setServiceType(OrderType.AUDIO);
        return req;
    }

    private RequisiteCatalog catalog(Long id, String name, String emoji) {
        RequisiteCatalog r = new RequisiteCatalog();
        r.setId(id);
        r.setName(name);
        r.setEmoji(emoji);
        r.setActive(true);
        r.setCreatedAt(Instant.now());
        return r;
    }

    private CreatorRequisite requisite(Long id, UUID creator, String name, String emoji,
                                       RequisiteSource source, OrderType serviceType) {
        CreatorRequisite cr = new CreatorRequisite();
        cr.setId(id);
        cr.setCreatorUserId(creator);
        cr.setName(name);
        cr.setEmoji(emoji);
        cr.setSource(source);
        cr.setServiceType(serviceType);
        cr.setCreatedAt(Instant.now());
        return cr;
    }
}
