package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.CreatorRequisite;
import uz.tabriko.domain.entity.RequisiteCatalog;
import uz.tabriko.domain.enums.OrderType;
import uz.tabriko.domain.enums.RequisiteSource;
import uz.tabriko.dto.request.AddCreatorRequisiteRequest;
import uz.tabriko.dto.request.AdminRequisiteRequest;
import uz.tabriko.dto.request.PatchRequisiteRequest;
import uz.tabriko.dto.response.AdminRequisiteResponse;
import uz.tabriko.dto.response.CreatorRequisiteResponse;
import uz.tabriko.dto.response.RequisiteItemResponse;
import uz.tabriko.repository.CreatorRequisiteRepository;
import uz.tabriko.repository.RequisiteCatalogRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequisiteService {

    private static final int MAX_CREATOR_REQUISITES = 20;

    private final RequisiteCatalogRepository catalogRepo;
    private final CreatorRequisiteRepository creatorRequisiteRepo;

    // --- Admin catalog ---

    @Transactional(readOnly = true)
    public List<AdminRequisiteResponse> getAdminRequisites() {
        return catalogRepo.findAll().stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AdminRequisiteResponse createRequisite(AdminRequisiteRequest req) {
        RequisiteCatalog r = new RequisiteCatalog();
        r.setName(req.getName().trim());
        r.setEmoji(req.getEmoji());
        r.setActive(req.getActive() == null || req.getActive());
        return toAdminResponse(catalogRepo.save(r));
    }

    @Transactional
    public AdminRequisiteResponse patchRequisite(Long id, PatchRequisiteRequest req) {
        RequisiteCatalog r = catalogRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Requisite not found"));
        if (req.getName() != null && !req.getName().isBlank()) {
            r.setName(req.getName().trim());
        }
        if (req.getEmoji() != null) {
            r.setEmoji(req.getEmoji().isBlank() ? null : req.getEmoji());
        }
        if (req.getActive() != null) {
            r.setActive(req.getActive());
        }
        return toAdminResponse(catalogRepo.save(r));
    }

    @Transactional
    public void deleteRequisite(Long id) {
        RequisiteCatalog r = catalogRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Requisite not found"));
        r.setActive(false);
        catalogRepo.save(r);
    }

    // --- Public catalog ---

    @Transactional(readOnly = true)
    public List<RequisiteItemResponse> getActiveRequisites() {
        return catalogRepo.findByActiveTrue().stream()
                .map(this::toPublicResponse)
                .collect(Collectors.toList());
    }

    // --- Creator self-service ---

    @Transactional(readOnly = true)
    public List<CreatorRequisiteResponse> getCreatorRequisites(UUID creatorId, OrderType serviceType) {
        return creatorRequisiteRepo
                .findByCreatorUserIdAndServiceTypeOrderByCreatedAtAsc(creatorId, serviceType).stream()
                .map(this::toCreatorRequisiteResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CreatorRequisiteResponse addCreatorRequisite(UUID creatorId, AddCreatorRequisiteRequest req) {
        OrderType serviceType = req.getServiceType();

        boolean hasCatalog = req.getCatalogId() != null;
        boolean hasCustom = req.getCustomName() != null && !req.getCustomName().isBlank();
        if (hasCatalog == hasCustom) {
            throw ApiException.badRequest("Exactly one of catalogId or customName must be provided");
        }

        long count = creatorRequisiteRepo.countByCreatorUserIdAndServiceType(creatorId, serviceType);
        if (count >= MAX_CREATOR_REQUISITES) {
            throw ApiException.badRequest("Maximum of " + MAX_CREATOR_REQUISITES + " requisites allowed per service type");
        }

        CreatorRequisite cr = new CreatorRequisite();
        cr.setCreatorUserId(creatorId);
        cr.setServiceType(serviceType);

        if (hasCatalog) {
            RequisiteCatalog catalog = catalogRepo.findById(req.getCatalogId())
                    .orElseThrow(() -> ApiException.notFound("Requisite catalog item not found"));
            if (!catalog.isActive()) {
                throw ApiException.badRequest("Requisite catalog item is not active");
            }
            if (creatorRequisiteRepo.existsByCreatorUserIdAndServiceTypeAndNameIgnoreCase(
                    creatorId, serviceType, catalog.getName())) {
                throw ApiException.conflict("Requisite with this name already exists");
            }
            cr.setName(catalog.getName());
            cr.setEmoji(catalog.getEmoji());
            cr.setCatalogId(catalog.getId());
            cr.setSource(RequisiteSource.CATALOG);
        } else {
            String name = req.getCustomName().trim();
            if (creatorRequisiteRepo.existsByCreatorUserIdAndServiceTypeAndNameIgnoreCase(
                    creatorId, serviceType, name)) {
                throw ApiException.conflict("Requisite with this name already exists");
            }
            cr.setName(name);
            cr.setEmoji(null);
            cr.setSource(RequisiteSource.CUSTOM);
        }

        return toCreatorRequisiteResponse(creatorRequisiteRepo.save(cr));
    }

    @Transactional
    public void deleteCreatorRequisite(UUID creatorId, Long requisiteId) {
        CreatorRequisite cr = creatorRequisiteRepo.findByIdAndCreatorUserId(requisiteId, creatorId)
                .orElseThrow(() -> ApiException.forbidden("Requisite not found or not owned by you"));
        creatorRequisiteRepo.delete(cr);
    }

    // --- For read models ---

    @Transactional(readOnly = true)
    public List<CreatorRequisite> findByCreatorId(UUID creatorId) {
        return creatorRequisiteRepo.findByCreatorUserIdOrderByCreatedAtAsc(creatorId);
    }

    // --- Mapping ---

    private AdminRequisiteResponse toAdminResponse(RequisiteCatalog r) {
        AdminRequisiteResponse resp = new AdminRequisiteResponse();
        resp.setId(r.getId());
        resp.setName(r.getName());
        resp.setEmoji(r.getEmoji());
        resp.setActive(r.isActive());
        resp.setCreatedAt(r.getCreatedAt());
        return resp;
    }

    private RequisiteItemResponse toPublicResponse(RequisiteCatalog r) {
        RequisiteItemResponse resp = new RequisiteItemResponse();
        resp.setId(r.getId());
        resp.setName(r.getName());
        resp.setEmoji(r.getEmoji());
        return resp;
    }

    private CreatorRequisiteResponse toCreatorRequisiteResponse(CreatorRequisite cr) {
        CreatorRequisiteResponse resp = new CreatorRequisiteResponse();
        resp.setId(cr.getId());
        resp.setName(cr.getName());
        resp.setEmoji(cr.getEmoji());
        resp.setSource(cr.getSource());
        resp.setServiceType(cr.getServiceType());
        return resp;
    }
}
