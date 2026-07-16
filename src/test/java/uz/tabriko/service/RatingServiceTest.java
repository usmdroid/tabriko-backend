package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.CreatorViolation;
import uz.tabriko.domain.enums.ViolationType;
import uz.tabriko.repository.CreatorProfileRepository;
import uz.tabriko.repository.CreatorViolationRepository;
import uz.tabriko.repository.ReviewRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock ReviewRepository reviewRepo;
    @Mock CreatorViolationRepository violationRepo;
    @Mock CreatorProfileRepository creatorProfileRepo;

    @InjectMocks RatingService ratingService;

    private UUID creatorId;
    private CreatorProfile profile;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        profile = new CreatorProfile();
    }

    private CreatorViolation violation(ViolationType type) {
        CreatorViolation v = new CreatorViolation();
        v.setCreatorId(creatorId);
        v.setOrderId(UUID.randomUUID());
        v.setType(type);
        v.setSeverity(type.getSeverity());
        return v;
    }

    // (a) 1 review (5 stars) + 1 DELIVERY_FAILURE → avgRating = (5+0)/2 = 2.50
    @Test
    void recompute_oneReviewPlusDeliveryFailure_averagesToTwoPointFive() {
        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(profile));
        when(reviewRepo.countByCreatorId(creatorId)).thenReturn(1L);
        when(reviewRepo.sumStarsByCreatorId(creatorId)).thenReturn(5L);
        when(violationRepo.findAllByCreatorId(creatorId)).thenReturn(List.of(violation(ViolationType.DELIVERY_FAILURE)));

        ratingService.recompute(creatorId);

        ArgumentCaptor<CreatorProfile> cap = ArgumentCaptor.forClass(CreatorProfile.class);
        verify(creatorProfileRepo).save(cap.capture());
        assertThat(cap.getValue().getAvgRating()).isEqualByComparingTo("2.50");
        assertThat(cap.getValue().getRatingCount()).isEqualTo(1);
    }

    // (b) REJECTION violation contributes 1 pseudo-star (severity=4, pseudo=5-4=1)
    @Test
    void recompute_rejectionViolationContributesOnePseudoStar() {
        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(profile));
        when(reviewRepo.countByCreatorId(creatorId)).thenReturn(0L);
        when(reviewRepo.sumStarsByCreatorId(creatorId)).thenReturn(0L);
        when(violationRepo.findAllByCreatorId(creatorId)).thenReturn(List.of(violation(ViolationType.REJECTION)));

        ratingService.recompute(creatorId);

        ArgumentCaptor<CreatorProfile> cap = ArgumentCaptor.forClass(CreatorProfile.class);
        verify(creatorProfileRepo).save(cap.capture());
        // (0 + 1) / (0 + 1) = 1.00
        assertThat(cap.getValue().getAvgRating()).isEqualByComparingTo("1.00");
    }

    // (c) NO_RESPONSE violation contributes 2 pseudo-stars (severity=3, pseudo=5-3=2)
    @Test
    void recompute_noResponseViolationContributesTwoPseudoStars() {
        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(profile));
        when(reviewRepo.countByCreatorId(creatorId)).thenReturn(0L);
        when(reviewRepo.sumStarsByCreatorId(creatorId)).thenReturn(0L);
        when(violationRepo.findAllByCreatorId(creatorId)).thenReturn(List.of(violation(ViolationType.NO_RESPONSE)));

        ratingService.recompute(creatorId);

        ArgumentCaptor<CreatorProfile> cap = ArgumentCaptor.forClass(CreatorProfile.class);
        verify(creatorProfileRepo).save(cap.capture());
        // (0 + 2) / (0 + 1) = 2.00
        assertThat(cap.getValue().getAvgRating()).isEqualByComparingTo("2.00");
    }

    // (d) Idempotency — existsByOrderId guard prevents double-counting
    // Simulated by RatingService summing violations from the repository:
    // if the same order appears only once in findAllByCreatorId, there's no double-count.
    // This test verifies that two calls to recompute with the same single violation produce the same result.
    @Test
    void recompute_calledTwiceWithSameViolation_doesNotDoubleCount() {
        CreatorViolation singleViolation = violation(ViolationType.DELIVERY_FAILURE);

        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(profile));
        when(reviewRepo.countByCreatorId(creatorId)).thenReturn(0L);
        when(reviewRepo.sumStarsByCreatorId(creatorId)).thenReturn(0L);
        when(violationRepo.findAllByCreatorId(creatorId)).thenReturn(List.of(singleViolation));

        ratingService.recompute(creatorId);
        ratingService.recompute(creatorId);

        ArgumentCaptor<CreatorProfile> cap = ArgumentCaptor.forClass(CreatorProfile.class);
        verify(creatorProfileRepo, times(2)).save(cap.capture());
        // Both calls should produce (0+0)/(0+1)=0.00, not cumulative
        cap.getAllValues().forEach(p ->
                assertThat(p.getAvgRating()).isEqualByComparingTo("0.00"));
    }
}
