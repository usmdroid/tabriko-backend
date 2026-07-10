package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.Category;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.Delivery;
import uz.tabriko.domain.entity.Order;
import uz.tabriko.domain.entity.PortfolioItem;
import uz.tabriko.dto.request.UpdateCreatorKycRequest;
import uz.tabriko.dto.request.UpdateCreatorProfileRequest;
import uz.tabriko.dto.request.UpdatePayoutRequest;
import uz.tabriko.dto.request.UpdateSocialRequest;
import uz.tabriko.dto.response.CreatorKycResponse;
import uz.tabriko.dto.response.CreatorResponse;
import uz.tabriko.dto.response.CreatorSelfProfileResponse;
import uz.tabriko.dto.response.EarningsResponse;
import uz.tabriko.dto.response.PortfolioItemResponse;
import uz.tabriko.infrastructure.media.MediaStorageService;
import uz.tabriko.repository.CategoryRepository;
import uz.tabriko.repository.CreatorProfileRepository;
import uz.tabriko.repository.DeliveryRepository;
import uz.tabriko.repository.OrderRepository;
import uz.tabriko.repository.PortfolioItemRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreatorService {

    private final CreatorProfileRepository creatorProfileRepo;
    private final PortfolioItemRepository portfolioRepo;
    private final CategoryRepository categoryRepo;
    private final OrderRepository orderRepo;
    private final DeliveryRepository deliveryRepo;
    private final MediaStorageService mediaStorage;
    private final WalletService walletService;
    private final UserMapper mapper;

    @Transactional(readOnly = true)
    public CreatorSelfProfileResponse getSelfProfile(UUID creatorId) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
            .orElseThrow(() -> ApiException.notFound("Creator profile not found"));
        List<PortfolioItem> items = portfolioRepo.findByCreatorId(creatorId);
        return mapper.toCreatorSelfProfileResponse(cp, items);
    }

    @Transactional
    public CreatorSelfProfileResponse updateProfile(UUID creatorId, UpdateCreatorProfileRequest req) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
            .orElseThrow(() -> ApiException.notFound("Creator profile not found"));

        if (req.getBio() != null) cp.setBio(req.getBio());
        if (req.getPriceFrom() != null) cp.setPriceFrom(req.getPriceFrom());
        if (req.getDeliveryDays() != null) cp.setDeliveryDays(req.getDeliveryDays());
        if (req.getOptions() != null) {
            cp.getOptions().clear();
            cp.getOptions().addAll(req.getOptions());
        }
        if (req.getAccepting() != null) cp.setAccepting(req.getAccepting());

        if (req.getCategoryId() != null) {
            Category cat = categoryRepo.findById(req.getCategoryId())
                .orElseThrow(() -> ApiException.notFound("Category not found"));
            cp.setCategory(cat);
        }

        creatorProfileRepo.save(cp);
        List<PortfolioItem> items = portfolioRepo.findByCreatorId(creatorId);
        return mapper.toCreatorSelfProfileResponse(cp, items);
    }

    @Transactional
    public CreatorSelfProfileResponse updateKycIdentity(UUID creatorId, String idNumber, MultipartFile file) {
        if (idNumber == null || idNumber.isBlank()) {
            throw ApiException.badRequest("idNumber is required");
        }
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
            .orElseThrow(() -> ApiException.notFound("Creator profile not found"));

        cp.setIdDocumentNumber(idNumber);
        if (file != null && !file.isEmpty()) {
            cp.setIdDocumentUrl(mediaStorage.store(file, "kyc"));
        }

        recomputeProfileComplete(cp);
        creatorProfileRepo.save(cp);
        return mapper.toCreatorSelfProfileResponse(cp, portfolioRepo.findByCreatorId(creatorId));
    }

    @Transactional
    public CreatorSelfProfileResponse updatePayout(UUID creatorId, UpdatePayoutRequest req) {
        if (req.getCard() == null && req.getAccount() == null) {
            throw ApiException.badRequest("card or account is required");
        }
        if (req.getHolder() == null || req.getHolder().isBlank()) {
            throw ApiException.badRequest("holder is required");
        }
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
            .orElseThrow(() -> ApiException.notFound("Creator profile not found"));

        if (req.getCard() != null) cp.setPayoutCard(req.getCard());
        if (req.getAccount() != null) cp.setPayoutAccount(req.getAccount());
        cp.setPayoutHolder(req.getHolder());

        recomputeProfileComplete(cp);
        creatorProfileRepo.save(cp);
        return mapper.toCreatorSelfProfileResponse(cp, portfolioRepo.findByCreatorId(creatorId));
    }

    @Transactional
    public CreatorSelfProfileResponse updateSocial(UUID creatorId, UpdateSocialRequest req) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
            .orElseThrow(() -> ApiException.notFound("Creator profile not found"));

        if (req.getTelegram() != null) cp.setSocialTelegram(req.getTelegram());
        if (req.getInstagram() != null) cp.setSocialInstagram(req.getInstagram());

        creatorProfileRepo.save(cp);
        return mapper.toCreatorSelfProfileResponse(cp, portfolioRepo.findByCreatorId(creatorId));
    }

    public void recomputeProfileComplete(CreatorProfile cp) {
        boolean hasId = cp.getIdDocumentNumber() != null && !cp.getIdDocumentNumber().isBlank()
            && cp.getIdDocumentUrl() != null && !cp.getIdDocumentUrl().isBlank();
        boolean hasPayout = (cp.getPayoutCard() != null && !cp.getPayoutCard().isBlank())
            || (cp.getPayoutAccount() != null && !cp.getPayoutAccount().isBlank());
        long portfolioCount = portfolioRepo.countByCreatorId(cp.getUserId());
        cp.setProfileComplete(hasId && hasPayout && portfolioCount >= 1);
    }

    public List<PortfolioItemResponse> getPortfolio(UUID creatorId) {
        return portfolioRepo.findByCreatorId(creatorId).stream()
            .map(mapper::toPortfolioResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public PortfolioItemResponse addPortfolio(UUID creatorId, String mediaUrl, MultipartFile file, boolean isPublic) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
            .orElseThrow(() -> ApiException.notFound("Creator profile not found"));

        String resolvedUrl;
        if (file != null && !file.isEmpty()) {
            resolvedUrl = mediaStorage.store(file, "portfolio");
        } else if (mediaUrl != null && !mediaUrl.isBlank()) {
            resolvedUrl = mediaUrl;
        } else {
            throw ApiException.badRequest("Either mediaUrl or file must be provided");
        }

        PortfolioItem item = new PortfolioItem();
        item.setCreator(cp.getUser());
        item.setMediaUrl(resolvedUrl);
        item.setPublic(isPublic);
        portfolioRepo.save(item);

        recomputeProfileComplete(cp);
        creatorProfileRepo.save(cp);

        return mapper.toPortfolioResponse(item);
    }

    @Transactional
    public PortfolioItemResponse addPortfolioFromOrder(UUID creatorId, UUID orderId, boolean isPublic) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
            .orElseThrow(() -> ApiException.notFound("Creator profile not found"));
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getCreator().getId().equals(creatorId)) {
            throw ApiException.forbidden("Not your order");
        }
        if (!order.isPortfolioConsent()) {
            throw ApiException.forbidden("Client has not given portfolio consent for this order");
        }

        Delivery delivery = deliveryRepo.findByOrderId(orderId)
            .orElseThrow(() -> ApiException.notFound("Delivery not found for this order"));

        PortfolioItem item = new PortfolioItem();
        item.setCreator(order.getCreator());
        item.setMediaUrl(delivery.getMediaUrlClean());
        item.setPublic(isPublic);
        item.setOrder(order);
        portfolioRepo.save(item);

        recomputeProfileComplete(cp);
        creatorProfileRepo.save(cp);

        return mapper.toPortfolioResponse(item);
    }

    @Transactional
    public void deletePortfolio(UUID creatorId, Long itemId) {
        PortfolioItem item = portfolioRepo.findById(itemId)
            .orElseThrow(() -> ApiException.notFound("Portfolio item not found"));
        if (!item.getCreator().getId().equals(creatorId)) {
            throw ApiException.forbidden("Not your portfolio item");
        }
        portfolioRepo.delete(item);

        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
            .orElseThrow(() -> ApiException.notFound("Creator profile not found"));
        recomputeProfileComplete(cp);
        creatorProfileRepo.save(cp);
    }

    @Transactional
    public PortfolioItemResponse updateVisibility(UUID creatorId, Long itemId, boolean isPublic) {
        PortfolioItem item = portfolioRepo.findById(itemId)
            .orElseThrow(() -> ApiException.notFound("Portfolio item not found"));
        if (!item.getCreator().getId().equals(creatorId)) {
            throw ApiException.forbidden("Not your portfolio item");
        }
        item.setPublic(isPublic);
        portfolioRepo.save(item);
        return mapper.toPortfolioResponse(item);
    }

    public EarningsResponse getEarnings(UUID creatorId) {
        return walletService.getEarnings(creatorId);
    }

    // --- KYC (contract endpoints: GET/PUT /creator/kyc, POST /creator/kyc/passport-file) ---

    public CreatorKycResponse getKyc(UUID creatorId) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
            .orElseThrow(() -> ApiException.notFound("Creator profile not found"));
        return mapper.toKycResponse(cp);
    }

    @Transactional
    public CreatorKycResponse updateKyc(UUID creatorId, UpdateCreatorKycRequest req) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
            .orElseThrow(() -> ApiException.notFound("Creator profile not found"));
        if (req.getPassportNumber() != null) cp.setIdDocumentNumber(req.getPassportNumber());
        if (req.getPassportFileUrl() != null) cp.setIdDocumentUrl(req.getPassportFileUrl());
        if (req.getPaymentCardNumber() != null) cp.setPayoutCard(req.getPaymentCardNumber());
        if (req.getPaymentHolderName() != null) cp.setPayoutHolder(req.getPaymentHolderName());
        if (req.getTelegram() != null) cp.setSocialTelegram(req.getTelegram());
        if (req.getInstagram() != null) cp.setSocialInstagram(req.getInstagram());
        recomputeProfileComplete(cp);
        creatorProfileRepo.save(cp);
        return mapper.toKycResponse(cp);
    }

    @Transactional
    public CreatorKycResponse uploadPassportFile(UUID creatorId, MultipartFile file) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
            .orElseThrow(() -> ApiException.notFound("Creator profile not found"));
        cp.setIdDocumentUrl(mediaStorage.store(file, "kyc"));
        recomputeProfileComplete(cp);
        creatorProfileRepo.save(cp);
        return mapper.toKycResponse(cp);
    }
}
