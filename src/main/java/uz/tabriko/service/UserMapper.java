package uz.tabriko.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uz.tabriko.domain.entity.*;
import uz.tabriko.dto.response.*;
import uz.tabriko.domain.enums.TransactionStatus;
import uz.tabriko.infrastructure.media.MediaStorageService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    private static final long SIGNED_URL_TTL_SECONDS = 3600L;

    @Autowired(required = false)
    private MediaStorageService mediaStorage;

    public UserResponse toResponse(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setPhone(user.getPhone());
        r.setName(user.getName());
        r.setEmail(user.getEmail());
        r.setRole(user.getRole());
        r.setStatus(user.getStatus());
        r.setBirthDate(user.getBirthDate());
        r.setCreatedAt(user.getCreatedAt());
        r.setAccountNumber(user.getAccountNumber());
        r.setAvatar(user.getAvatarUrl());
        return r;
    }

    public CategoryResponse toCategoryResponse(Category c) {
        CategoryResponse r = new CategoryResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setNameUz(c.getName());
        r.setNameRu(c.getNameRu());
        r.setNameEn(c.getNameEn());
        r.setIconUrl(c.getIconUrl());
        return r;
    }

    public AdminCategoryResponse toAdminCategoryResponse(Category c) {
        AdminCategoryResponse r = new AdminCategoryResponse();
        r.setId(c.getId());
        r.setNameUz(c.getName());
        r.setNameRu(c.getNameRu());
        r.setNameEn(c.getNameEn());
        r.setIconUrl(c.getIconUrl());
        r.setArchived(c.isArchived());
        return r;
    }

    public CreatorResponse toCreatorResponse(CreatorProfile cp, List<PortfolioItem> portfolio) {
        return toCreatorResponse(cp, portfolio, List.of());
    }

    public CreatorResponse toCreatorResponse(CreatorProfile cp, List<PortfolioItem> portfolio,
                                              List<CreatorServiceOffering> services) {
        CreatorResponse r = new CreatorResponse();
        r.setId(cp.getUserId());
        r.setName(cp.getUser().getName());
        r.setAvatarUrl(cp.getAvatarUrl());
        r.setBannerUrl(cp.getBannerUrl());
        r.setBio(cp.getBio());
        if (cp.getCategory() != null) {
            r.setCategory(toCategoryResponse(cp.getCategory()));
        }
        r.setAvgRating(cp.getAvgRating());
        r.setRatingCount(cp.getRatingCount());
        r.setDeliveryDays(cp.getDeliveryDays());
        r.setTop(cp.isTop());
        r.setExclusive(cp.isExclusive());
        r.setVerified(cp.isVerified());
        r.setAccepting(cp.isAccepting());
        r.setTier(cp.getTier());
        r.setOptions(new java.util.HashSet<>(cp.getOptions()));
        applyServicePricing(services, r::setServices, r::setPriceFrom, r::setOriginalPriceFrom, r::setOnSale);
        r.setPortfolio(portfolio.stream().map(this::toPortfolioResponse).collect(Collectors.toList()));
        r.setPhone(cp.getUser().getPhone());
        r.setStatus(cp.getUser().getStatus().name().toLowerCase());
        r.setCreatedAt(cp.getUser().getCreatedAt());
        return r;
    }

    public CreatorSelfProfileResponse toCreatorSelfProfileResponse(CreatorProfile cp, List<PortfolioItem> portfolio) {
        return toCreatorSelfProfileResponse(cp, portfolio, List.of());
    }

    public CreatorSelfProfileResponse toCreatorSelfProfileResponse(CreatorProfile cp, List<PortfolioItem> portfolio,
                                                                    List<CreatorServiceOffering> services) {
        CreatorSelfProfileResponse r = new CreatorSelfProfileResponse();
        r.setId(cp.getUserId());
        r.setName(cp.getUser().getName());
        r.setAvatarUrl(cp.getAvatarUrl());
        r.setBannerUrl(cp.getBannerUrl());
        r.setBio(cp.getBio());
        if (cp.getCategory() != null) {
            r.setCategory(toCategoryResponse(cp.getCategory()));
        }
        r.setAvgRating(cp.getAvgRating());
        r.setRatingCount(cp.getRatingCount());
        r.setDeliveryDays(cp.getDeliveryDays());
        r.setTop(cp.isTop());
        r.setExclusive(cp.isExclusive());
        r.setVerified(cp.isVerified());
        r.setAccepting(cp.isAccepting());
        r.setTier(cp.getTier());
        r.setOptions(new java.util.HashSet<>(cp.getOptions()));
        applyServicePricing(services, r::setServices, r::setPriceFrom, r::setOriginalPriceFrom, r::setOnSale);
        r.setPortfolio(portfolio.stream().map(this::toPortfolioResponse).collect(Collectors.toList()));
        r.setStatus(cp.getUser().getStatus().name().toLowerCase());
        r.setCreatedAt(cp.getUser().getCreatedAt());

        boolean hasId = cp.getIdDocumentNumber() != null && !cp.getIdDocumentNumber().isBlank();
        r.setIdProvided(hasId);
        if (hasId) {
            r.setIdDocumentNumberMasked(maskId(cp.getIdDocumentNumber()));
        }
        r.setIdDocumentUrl(cp.getIdDocumentUrl());

        boolean hasPayout = (cp.getPayoutCard() != null && !cp.getPayoutCard().isBlank())
                || (cp.getPayoutAccount() != null && !cp.getPayoutAccount().isBlank());
        r.setPayoutProvided(hasPayout);
        if (cp.getPayoutCard() != null && !cp.getPayoutCard().isBlank()) {
            r.setPayoutCardMasked(maskCard(cp.getPayoutCard()));
        }
        if (cp.getPayoutAccount() != null && !cp.getPayoutAccount().isBlank()) {
            r.setPayoutAccountMasked(maskAccount(cp.getPayoutAccount()));
        }
        r.setPayoutHolder(cp.getPayoutHolder());

        r.setSocialTelegram(cp.getSocialTelegram());
        r.setSocialInstagram(cp.getSocialInstagram());

        List<String> missing = computeMissingItems(cp, portfolio);
        r.setMissing(missing);
        r.setProfileComplete(missing.isEmpty());

        return r;
    }

    public CreatorServiceResponse toCreatorServiceResponse(CreatorServiceOffering svc) {
        Instant now = Instant.now();
        CreatorServiceResponse r = new CreatorServiceResponse();
        r.setType(svc.getType());
        r.setPrice(svc.getPrice());
        r.setEffectivePrice(ServicePricingCalculator.effectivePrice(svc, now));
        boolean onSale = ServicePricingCalculator.isOnSale(svc, now);
        r.setOnSale(onSale);
        if (onSale) {
            r.setDiscountPercent(ServicePricingCalculator.discountPercent(svc, now));
            r.setDiscountEndsAt(svc.getDiscountEndsAt());
        }
        r.setDeliveryDays(svc.getDeliveryDays());
        r.setAccepting(svc.isAccepting());
        return r;
    }

    private void applyServicePricing(List<CreatorServiceOffering> services,
                                      Consumer<List<CreatorServiceResponse>> servicesSetter,
                                      Consumer<BigDecimal> priceFromSetter,
                                      Consumer<BigDecimal> originalPriceFromSetter,
                                      Consumer<Boolean> onSaleSetter) {
        Instant now = Instant.now();
        servicesSetter.accept(services.stream().map(this::toCreatorServiceResponse).collect(Collectors.toList()));

        List<CreatorServiceOffering> accepting = services.stream()
                .filter(CreatorServiceOffering::isAccepting)
                .collect(Collectors.toList());
        priceFromSetter.accept(accepting.stream()
                .map(s -> ServicePricingCalculator.effectivePrice(s, now))
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO));
        originalPriceFromSetter.accept(accepting.stream()
                .map(CreatorServiceOffering::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO));
        onSaleSetter.accept(accepting.stream().anyMatch(s -> ServicePricingCalculator.isOnSale(s, now)));
    }

    public CreatorContactResponse toContactResponse(CreatorContact c) {
        CreatorContactResponse r = new CreatorContactResponse();
        r.setId(c.getId());
        r.setPhone(c.getPhone());
        r.setLabel(c.getLabel());
        r.setCreatedAt(c.getCreatedAt());
        return r;
    }

    public List<CreatorContactResponse> toContactResponses(List<CreatorContact> contacts) {
        return contacts.stream().map(this::toContactResponse).collect(Collectors.toList());
    }

    public CreatorKycResponse toKycResponse(CreatorProfile cp) {
        CreatorKycResponse r = new CreatorKycResponse();
        r.setPassportNumber(maskId(cp.getIdDocumentNumber()));
        r.setPassportFileUrl(cp.getIdDocumentUrl());
        r.setPaymentCardNumber(maskCard(cp.getPayoutCard()));
        r.setPaymentHolderName(cp.getPayoutHolder());
        r.setTelegram(cp.getSocialTelegram());
        r.setInstagram(cp.getSocialInstagram());
        return r;
    }

    private List<String> computeMissingItems(CreatorProfile cp, List<PortfolioItem> portfolio) {
        List<String> missing = new ArrayList<>();
        if (cp.getBio() == null || cp.getBio().isBlank()) missing.add("bio");
        if (cp.getPriceFrom() == null || cp.getPriceFrom().compareTo(BigDecimal.ZERO) == 0) missing.add("priceFrom");
        if (cp.getDeliveryDays() == 0) missing.add("deliveryDays");
        boolean hasSocial = (cp.getSocialTelegram() != null && !cp.getSocialTelegram().isBlank())
                || (cp.getSocialInstagram() != null && !cp.getSocialInstagram().isBlank());
        if (!hasSocial) missing.add("social");
        if (portfolio == null || portfolio.isEmpty()) missing.add("portfolio");
        boolean hasPassport = cp.getIdDocumentNumber() != null && !cp.getIdDocumentNumber().isBlank()
                && cp.getIdDocumentUrl() != null && !cp.getIdDocumentUrl().isBlank();
        if (!hasPassport) missing.add("passport");
        boolean hasPayment = (cp.getPayoutCard() != null && !cp.getPayoutCard().isBlank())
                || (cp.getPayoutAccount() != null && !cp.getPayoutAccount().isBlank());
        if (!hasPayment) missing.add("payment");
        return missing;
    }

    private String maskId(String id) {
        if (id == null || id.length() < 4) return "****";
        String suffix = id.substring(id.length() - 4);
        String prefix = "*".repeat(id.length() - 4);
        return prefix + suffix;
    }

    private String maskCard(String card) {
        if (card == null || card.length() < 4) return "****";
        return "**** **** **** " + card.substring(card.length() - 4);
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }

    public PortfolioItemResponse toPortfolioResponse(PortfolioItem item) {
        PortfolioItemResponse r = new PortfolioItemResponse();
        r.setId(item.getId());
        r.setMediaUrl(item.getMediaUrl());
        r.setPublic(item.isPublic());
        if (item.getOrder() != null) {
            r.setOrderId(item.getOrder().getId());
        }
        return r;
    }

    public WalletTransactionResponse toWalletTxResponse(WalletTransaction tx) {
        WalletTransactionResponse r = new WalletTransactionResponse();
        r.setId(tx.getId());
        r.setAmount(tx.getAmount());
        r.setType(tx.getType().name());
        r.setStatus(tx.getStatus().name());
        if (tx.getOrder() != null) {
            r.setOrderId(tx.getOrder().getId());
        }
        r.setCreatedAt(tx.getCreatedAt());
        return r;
    }

    public ReportResponse toReportResponse(Report report) {
        ReportResponse r = new ReportResponse();
        r.setId(report.getId());
        r.setReporterId(report.getReporter().getId());
        r.setTargetType(report.getTargetType().name());
        r.setTargetId(report.getTargetId());
        r.setReason(report.getReason());
        r.setStatus(report.getStatus().name());
        r.setCreatedAt(report.getCreatedAt());
        return r;
    }

    public OrderResponse toOrderResponse(Order o, Delivery delivery, UUID userId) {
        OrderResponse r = new OrderResponse();
        r.setId(o.getId());
        r.setClientId(o.getClient().getId());
        r.setClientName(o.getClient().getName());
        r.setClientPhone(o.getClient().getPhone());
        r.setCreatorId(o.getCreator().getId());
        r.setCreatorName(o.getCreator().getName());
        r.setCreatorPhone(o.getCreator().getPhone());
        r.setType(o.getType());
        r.setOption(o.getOption());
        r.setRecipientName(o.getRecipientName());
        r.setRecipientOccasion(o.getRecipientOccasion());
        r.setCustomText(o.getCustomText());
        r.setPublic(o.isPublic());
        r.setPrice(o.getPrice());
        r.setStatus(o.getStatus());
        r.setDeadline(o.getDeadline());
        r.setCreatedAt(o.getCreatedAt());
        r.setRejectionReason(o.getRejectionReason());
        if (delivery != null) {
            r.setDelivery(toDeliveryResponse(delivery, userId));
        }
        return r;
    }

    public DeliveryResponse toDeliveryResponse(Delivery d, UUID userId) {
        DeliveryResponse r = new DeliveryResponse();
        r.setId(d.getId());
        if (userId == null) {
            throw new IllegalStateException("userId must not be null when a delivery is present");
        }
        String rawUrl = d.getMediaUrlClean();
        r.setMediaUrl(mediaStorage != null
            ? mediaStorage.signedUrl(rawUrl, userId, SIGNED_URL_TTL_SECONDS)
            : rawUrl);
        r.setWatermarked(false);
        r.setDeliveredAt(d.getDeliveredAt());
        return r;
    }

    public ReviewResponse toReviewResponse(Review rev) {
        ReviewResponse r = new ReviewResponse();
        r.setId(rev.getId());
        r.setClientName(rev.getClient().getName());
        r.setStars(rev.getStars());
        r.setComment(rev.getComment());
        r.setCreatedAt(rev.getCreatedAt());
        return r;
    }

    public NotificationResponse toNotificationResponse(Notification n) {
        NotificationResponse r = new NotificationResponse();
        r.setId(n.getId());
        r.setTitle(n.getTitle());
        r.setBody(n.getBody());
        r.setType(n.getType());
        r.setRead(n.isRead());
        r.setCreatedAt(n.getCreatedAt());
        return r;
    }
}
