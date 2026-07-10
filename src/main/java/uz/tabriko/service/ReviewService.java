package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.Order;
import uz.tabriko.domain.entity.Review;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.NotificationType;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.dto.request.CreateReviewRequest;
import uz.tabriko.dto.response.PageResponse;
import uz.tabriko.dto.response.ReviewResponse;
import uz.tabriko.repository.CreatorProfileRepository;
import uz.tabriko.repository.OrderRepository;
import uz.tabriko.repository.ReviewRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepo;
    private final OrderRepository orderRepo;
    private final CreatorProfileRepository creatorProfileRepo;
    private final NotificationService notificationService;
    private final UserMapper mapper;

    @Transactional
    public ReviewResponse createReview(UUID clientId, UUID orderId, CreateReviewRequest req) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getClient().getId().equals(clientId)) {
            throw ApiException.forbidden("Not your order");
        }
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw ApiException.badRequest("Can only review accepted orders");
        }
        if (reviewRepo.findByOrderId(orderId).isPresent()) {
            throw ApiException.conflict("Order already reviewed");
        }

        Review review = new Review();
        review.setOrder(order);
        review.setClient(order.getClient());
        review.setCreator(order.getCreator());
        review.setStars(req.getStars());
        review.setComment(req.getComment());
        reviewRepo.save(review);

        updateCreatorRating(order.getCreator().getId());

        notificationService.sendNotification(
                order.getCreator().getId(),
                "New review",
                order.getClient().getName() + " left you a " + req.getStars() + "-star review",
                NotificationType.REVIEW_RECEIVED
        );

        return mapper.toReviewResponse(review);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getCreatorReviews(UUID creatorId, int page, int size) {
        return PageResponse.of(
                reviewRepo.findByCreatorIdOrderByCreatedAtDesc(creatorId, PageRequest.of(page, size)),
                mapper::toReviewResponse
        );
    }

    private void updateCreatorRating(UUID creatorId) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId).orElse(null);
        if (cp == null) return;
        Double avg = reviewRepo.calculateAvgRating(creatorId);
        long count = reviewRepo.countByCreatorId(creatorId);
        cp.setAvgRating(avg != null ? BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        cp.setRatingCount((int) count);
        creatorProfileRepo.save(cp);
    }
}
