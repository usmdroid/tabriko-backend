package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.Order;
import uz.tabriko.domain.entity.Review;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.dto.request.CreateReviewRequest;
import uz.tabriko.repository.CreatorProfileRepository;
import uz.tabriko.repository.OrderRepository;
import uz.tabriko.repository.ReviewRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ReviewRepository reviewRepo;
    @Mock OrderRepository orderRepo;
    @Mock CreatorProfileRepository creatorProfileRepo;
    @Mock NotificationService notificationService;
    @Mock UserMapper mapper;

    @InjectMocks ReviewService reviewService;

    private UUID clientId;
    private UUID orderId;
    private User client;
    private User creator;
    private Order order;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        client = new User();
        client.setId(clientId);
        client.setName("Client");
        creator = new User();
        creator.setId(UUID.randomUUID());

        order = new Order();
        order.setClient(client);
        order.setCreator(creator);
        order.setStatus(OrderStatus.ACCEPTED);
    }

    private CreateReviewRequest request(int stars) {
        CreateReviewRequest req = new CreateReviewRequest();
        req.setStars(stars);
        req.setComment("Great job");
        return req;
    }

    @Test
    void createReview_notOwner_throwsForbidden() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> reviewService.createReview(UUID.randomUUID(), orderId, request(5)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Not your order");

        verify(reviewRepo, never()).save(any());
    }

    @Test
    void createReview_orderNotAccepted_throwsBadRequest() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> reviewService.createReview(clientId, orderId, request(5)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Can only review accepted orders");

        verify(reviewRepo, never()).save(any());
    }

    @Test
    void createReview_alreadyReviewed_throwsConflict() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(reviewRepo.findByOrderId(orderId)).thenReturn(Optional.of(new Review()));

        assertThatThrownBy(() -> reviewService.createReview(clientId, orderId, request(5)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already reviewed");

        verify(reviewRepo, never()).save(any());
    }

    @Test
    void createReview_success_savesReviewUpdatesRatingAndNotifiesCreator() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(reviewRepo.findByOrderId(orderId)).thenReturn(Optional.empty());

        CreatorProfile profile = new CreatorProfile();
        profile.setUser(creator);
        when(creatorProfileRepo.findByUserId(creator.getId())).thenReturn(Optional.of(profile));
        when(reviewRepo.calculateAvgRating(creator.getId())).thenReturn(4.5);
        when(reviewRepo.countByCreatorId(creator.getId())).thenReturn(2L);

        reviewService.createReview(clientId, orderId, request(5));

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepo).save(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getStars()).isEqualTo(5);
        assertThat(reviewCaptor.getValue().getClient()).isEqualTo(client);
        assertThat(reviewCaptor.getValue().getCreator()).isEqualTo(creator);

        ArgumentCaptor<CreatorProfile> profileCaptor = ArgumentCaptor.forClass(CreatorProfile.class);
        verify(creatorProfileRepo).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getAvgRating()).isEqualByComparingTo("4.50");
        assertThat(profileCaptor.getValue().getRatingCount()).isEqualTo(2);

        verify(notificationService).sendNotification(
                eq(creator.getId()), any(), any(), eq(uz.tabriko.domain.enums.NotificationType.REVIEW_RECEIVED));
    }


    @Test
    void createReview_noRatingsYet_avgRatingDefaultsToZero() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(reviewRepo.findByOrderId(orderId)).thenReturn(Optional.empty());

        CreatorProfile profile = new CreatorProfile();
        profile.setUser(creator);
        when(creatorProfileRepo.findByUserId(creator.getId())).thenReturn(Optional.of(profile));
        when(reviewRepo.calculateAvgRating(creator.getId())).thenReturn(null);
        when(reviewRepo.countByCreatorId(creator.getId())).thenReturn(0L);

        reviewService.createReview(clientId, orderId, request(5));

        ArgumentCaptor<CreatorProfile> profileCaptor = ArgumentCaptor.forClass(CreatorProfile.class);
        verify(creatorProfileRepo).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getAvgRating()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createReview_creatorProfileMissing_doesNotThrowOrSaveProfile() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(reviewRepo.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(creatorProfileRepo.findByUserId(creator.getId())).thenReturn(Optional.empty());

        reviewService.createReview(clientId, orderId, request(5));

        verify(reviewRepo).save(any());
        verify(creatorProfileRepo, never()).save(any());
    }
}
