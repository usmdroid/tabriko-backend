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

    // ===== Add from catalog (happy path) =====

    @Test
    void addCreatorRequisite_fromCatalog_happyPath() {
        RequisiteCatalog catalog = catalogCatalog(1L, "Gul", "🌸");

        when(catalogRepo.findById(1L)).thenReturn(Optional.of(catalog));
        when(creatorRequisiteRepo.countByCreatorUserId(creatorId)).thenReturn(0L);
        when(creatorRequisiteRepo.existsByCreatorUserIdAndNameIgnoreCase(creatorId, "Gul")).thenReturn(false);

        CreatorRequisite saved = creatorRequisite(10L, creatorId, "Gul", "🌸", RequisiteSource.CATALOG);
        when(creatorRequisiteRepo.save(any())).thenReturn(saved);

        AddCreatorRequisiteRequest req = new AddCreatorRequisiteRequest();
        req.setCatalogId(1L);

        CreatorRequisiteResponse resp = requisiteService.addCreatorRequisite(creatorId, req);

        assertThat(resp.getName()).isEqualTo("Gul");
        assertThat(resp.getEmoji()).isEqualTo("🌸");
        assertThat(resp.getSource()).isEqualTo(RequisiteSource.CATALOG);

        ArgumentCaptor<CreatorRequisite> captor = ArgumentCaptor.forClass(CreatorRequisite.class);
        verify(creatorRequisiteRepo).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(RequisiteSource.CATALOG);
        assertThat(captor.getValue().getCatalogId()).isEqualTo(1L);
    }

    // ===== Add custom name (happy path) =====

    @Test
    void addCreatorRequisite_customName_happyPath() {
        when(creatorRequisiteRepo.countByCreatorUserId(creatorId)).thenReturn(5L);
        when(creatorRequisiteRepo.existsByCreatorUserIdAndNameIgnoreCase(creatorId, "Sham")).thenReturn(false);

        CreatorRequisite saved = creatorRequisite(11L, creatorId, "Sham", null, RequisiteSource.CUSTOM);
        when(creatorRequisiteRepo.save(any())).thenReturn(saved);

        AddCreatorRequisiteRequest req = new AddCreatorRequisiteRequest();
        req.setCustomName("Sham");

        CreatorRequisiteResponse resp = requisiteService.addCreatorRequisite(creatorId, req);

        assertThat(resp.getName()).isEqualTo("Sham");
        assertThat(resp.getEmoji()).isNull();
        assertThat(resp.getSource()).isEqualTo(RequisiteSource.CUSTOM);

        ArgumentCaptor<CreatorRequisite> captor = ArgumentCaptor.forClass(CreatorRequisite.class);
        verify(creatorRequisiteRepo).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(RequisiteSource.CUSTOM);
        assertThat(captor.getValue().getCatalogId()).isNull();
    }

    // ===== Duplicate rejection (case-insensitive) =====

    @Test
    void addCreatorRequisite_catalogDuplicate_throwsConflict() {
        RequisiteCatalog catalog = catalogCatalog(1L, "Gul", "🌸");

        when(catalogRepo.findById(1L)).thenReturn(Optional.of(catalog));
        when(creatorRequisiteRepo.countByCreatorUserId(creatorId)).thenReturn(2L);
        when(creatorRequisiteRepo.existsByCreatorUserIdAndNameIgnoreCase(creatorId, "Gul")).thenReturn(true);

        AddCreatorRequisiteRequest req = new AddCreatorRequisiteRequest();
        req.setCatalogId(1L);

        assertThatThrownBy(() -> requisiteService.addCreatorRequisite(creatorId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void addCreatorRequisite_customDuplicate_throwsConflict() {
        when(creatorRequisiteRepo.countByCreatorUserId(creatorId)).thenReturn(2L);
        when(creatorRequisiteRepo.existsByCreatorUserIdAndNameIgnoreCase(creatorId, "Tort")).thenReturn(true);

        AddCreatorRequisiteRequest req = new AddCreatorRequisiteRequest();
        req.setCustomName("Tort");

        assertThatThrownBy(() -> requisiteService.addCreatorRequisite(creatorId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    // ===== Max-20 limit rejection =====

    @Test
    void addCreatorRequisite_atLimit_throwsBadRequest() {
        when(creatorRequisiteRepo.countByCreatorUserId(creatorId)).thenReturn(20L);

        AddCreatorRequisiteRequest req = new AddCreatorRequisiteRequest();
        req.setCustomName("NewItem");

        assertThatThrownBy(() -> requisiteService.addCreatorRequisite(creatorId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Maximum");
    }

    // ===== Delete own requisite =====

    @Test
    void deleteCreatorRequisite_ownRequisite_deletesSuccessfully() {
        CreatorRequisite cr = creatorRequisite(99L, creatorId, "Gul", "🌸", RequisiteSource.CATALOG);
        when(creatorRequisiteRepo.findByIdAndCreatorUserId(99L, creatorId)).thenReturn(Optional.of(cr));

        requisiteService.deleteCreatorRequisite(creatorId, 99L);

        verify(creatorRequisiteRepo).delete(cr);
    }

    // ===== Delete another creator's requisite -> forbidden =====

    @Test
    void deleteCreatorRequisite_notOwned_throwsForbidden() {
        when(creatorRequisiteRepo.findByIdAndCreatorUserId(99L, creatorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requisiteService.deleteCreatorRequisite(creatorId, 99L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not owned");
    }

    // ===== Public creator profile response includes requisites =====

    @Test
    void getCreatorRequisites_returnsListForCreator() {
        CreatorRequisite r1 = creatorRequisite(1L, creatorId, "Gul", "🌸", RequisiteSource.CATALOG);
        CreatorRequisite r2 = creatorRequisite(2L, creatorId, "Custom", null, RequisiteSource.CUSTOM);
        when(creatorRequisiteRepo.findByCreatorUserIdOrderByCreatedAtAsc(creatorId)).thenReturn(List.of(r1, r2));

        List<CreatorRequisiteResponse> result = requisiteService.getCreatorRequisites(creatorId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Gul");
        assertThat(result.get(0).getEmoji()).isEqualTo("🌸");
        assertThat(result.get(1).getName()).isEqualTo("Custom");
        assertThat(result.get(1).getEmoji()).isNull();
    }

    // ===== Admin creator-detail response includes requisites (via findByCreatorId) =====

    @Test
    void findByCreatorId_returnsRequisitesForProfile() {
        CreatorRequisite r = creatorRequisite(5L, creatorId, "Tort", "🎂", RequisiteSource.CATALOG);
        when(creatorRequisiteRepo.findByCreatorUserIdOrderByCreatedAtAsc(creatorId)).thenReturn(List.of(r));

        List<CreatorRequisite> result = requisiteService.findByCreatorId(creatorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Tort");
    }

    // ===== Active catalog endpoint =====

    @Test
    void getActiveRequisites_returnsOnlyActiveItems() {
        RequisiteCatalog active = catalogCatalog(1L, "Gul", "🌸");
        when(catalogRepo.findByActiveTrue()).thenReturn(List.of(active));

        List<RequisiteItemResponse> result = requisiteService.getActiveRequisites();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Gul");
        assertThat(result.get(0).getEmoji()).isEqualTo("🌸");
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

    // ===== Both catalogId and customName provided -> bad request =====

    @Test
    void addCreatorRequisite_bothProvided_throwsBadRequest() {
        AddCreatorRequisiteRequest req = new AddCreatorRequisiteRequest();
        req.setCatalogId(1L);
        req.setCustomName("Custom");

        assertThatThrownBy(() -> requisiteService.addCreatorRequisite(creatorId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Exactly one");
    }

    // ===== Neither provided -> bad request =====

    @Test
    void addCreatorRequisite_neitherProvided_throwsBadRequest() {
        AddCreatorRequisiteRequest req = new AddCreatorRequisiteRequest();

        assertThatThrownBy(() -> requisiteService.addCreatorRequisite(creatorId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Exactly one");
    }

    // ===== Catalog item not active -> bad request =====

    @Test
    void addCreatorRequisite_inactiveCatalogItem_throwsBadRequest() {
        RequisiteCatalog catalog = catalogCatalog(1L, "Gul", "🌸");
        catalog.setActive(false);

        when(catalogRepo.findById(1L)).thenReturn(Optional.of(catalog));
        when(creatorRequisiteRepo.countByCreatorUserId(creatorId)).thenReturn(0L);

        AddCreatorRequisiteRequest req = new AddCreatorRequisiteRequest();
        req.setCatalogId(1L);

        assertThatThrownBy(() -> requisiteService.addCreatorRequisite(creatorId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not active");
    }

    // --- helpers ---

    private RequisiteCatalog catalogCatalog(Long id, String name, String emoji) {
        RequisiteCatalog r = new RequisiteCatalog();
        r.setId(id);
        r.setName(name);
        r.setEmoji(emoji);
        r.setActive(true);
        r.setCreatedAt(Instant.now());
        return r;
    }

    private CreatorRequisite creatorRequisite(Long id, UUID creatorId, String name, String emoji, RequisiteSource source) {
        CreatorRequisite cr = new CreatorRequisite();
        cr.setId(id);
        cr.setCreatorUserId(creatorId);
        cr.setName(name);
        cr.setEmoji(emoji);
        cr.setSource(source);
        cr.setCreatedAt(Instant.now());
        return cr;
    }
}
