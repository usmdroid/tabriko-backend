package uz.tabriko.service;

import org.springframework.stereotype.Component;
import uz.tabriko.domain.entity.*;
import uz.tabriko.dto.response.*;
import uz.tabriko.domain.enums.TransactionStatus;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setPhone(user.getPhone());
        r.setName(user.getName());
        r.setEmail(user.getEmail());
        r.setRole(user.getRole());
        r.setStatus(user.getStatus());
        r.setCreatedAt(user.getCreatedAt());
        return r;
    }

    public CategoryResponse toCategoryResponse(Category c) {
        CategoryResponse r = new CategoryResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setIconUrl(c.getIconUrl());
        return r;
    }

    public CreatorResponse toCreatorResponse(CreatorProfile cp, List<PortfolioItem> portfolio) {
        CreatorResponse r = new CreatorResponse();
        r.setId(cp.getUserId());
        r.setName(cp.getUser().getName());
        r.setAvatarUrl(cp.getAvatarUrl());
        r.setBio(cp.getBio());
        if (cp.getCategory() != null) {
            r.setCategory(toCategoryResponse(cp.getCategory()));
        }
        r.setAvgRating(cp.getAvgRating());
        r.setRatingCount(cp.getRatingCount());
        r.setPriceFrom(cp.getPriceFrom());
        r.setDeliveryDays(cp.getDeliveryDays());
        r.setTop(cp.isTop());
        r.setExclusive(cp.isExclusive());
        r.setVerified(cp.isVerified());
        r.setAccepting(cp.isAccepting());
        r.setOptions(cp.getOptions());
        r.setPortfolio(portfolio.stream().map(this::toPortfolioResponse).collect(Collectors.toList()));
        return r;
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

    public OrderResponse toOrderResponse(Order o, Delivery delivery) {
        OrderResponse r = new OrderResponse();
        r.setId(o.getId());
        r.setClientId(o.getClient().getId());
        r.setClientName(o.getClient().getName());
        r.setCreatorId(o.getCreator().getId());
        r.setCreatorName(o.getCreator().getName());
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
            r.setDelivery(toDeliveryResponse(delivery));
        }
        return r;
    }

    public DeliveryResponse toDeliveryResponse(Delivery d) {
        DeliveryResponse r = new DeliveryResponse();
        r.setId(d.getId());
        // Show clean URL if watermark released, otherwise watermarked
        r.setMediaUrl(d.isWatermarked() ? d.getMediaUrlWatermarked() : d.getMediaUrlClean());
        r.setWatermarked(d.isWatermarked());
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
