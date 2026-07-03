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
import uz.tabriko.dto.request.UpdateCreatorProfileRequest;
import uz.tabriko.dto.response.CreatorResponse;
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

    public CreatorResponse getProfile(UUID creatorId) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
            .orElseThrow(() -> ApiException.notFound("Creator profile not found"));
        List<PortfolioItem> items = portfolioRepo.findByCreatorId(creatorId);
        return mapper.toCreatorResponse(cp, items);
    }

    @Transactional
    public CreatorResponse updateProfile(UUID creatorId, UpdateCreatorProfileRequest req) {
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
        return mapper.toCreatorResponse(cp, items);
    }

    public List<PortfolioItemResponse> getPortfolio(UUID creatorId) {
        return portfolioRepo.findByCreatorId(creatorId).stream()
            .map(mapper::toPortfolioResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public PortfolioItemResponse addPortfolio(UUID creatorId, String mediaUrl, MultipartFile file, boolean isPublic) {
        creatorProfileRepo.findByUserId(creatorId)
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
        item.setCreator(creatorProfileRepo.findByUserId(creatorId).get().getUser());
        item.setMediaUrl(resolvedUrl);
        item.setPublic(isPublic);
        portfolioRepo.save(item);
        return mapper.toPortfolioResponse(item);
    }

    @Transactional
    public PortfolioItemResponse addPortfolioFromOrder(UUID creatorId, UUID orderId, boolean isPublic) {
        creatorProfileRepo.findByUserId(creatorId)
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
}
