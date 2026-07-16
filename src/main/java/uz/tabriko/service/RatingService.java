package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.CreatorViolation;
import uz.tabriko.repository.CreatorProfileRepository;
import uz.tabriko.repository.CreatorViolationRepository;
import uz.tabriko.repository.ReviewRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final ReviewRepository reviewRepo;
    private final CreatorViolationRepository violationRepo;
    private final CreatorProfileRepository creatorProfileRepo;

    @Transactional
    public void recompute(UUID creatorId) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId).orElse(null);
        if (cp == null) return;

        long reviewCount = reviewRepo.countByCreatorId(creatorId);
        long starSum = reviewRepo.sumStarsByCreatorId(creatorId);

        List<CreatorViolation> violations = violationRepo.findAllByCreatorId(creatorId);
        long violationCount = violations.size();
        long violationPseudoSum = violations.stream()
                .mapToInt(v -> Math.max(0, Math.min(5, 5 - v.getSeverity())))
                .sum();

        long totalCount = reviewCount + violationCount;
        if (totalCount == 0) {
            cp.setAvgRating(BigDecimal.ZERO);
        } else {
            BigDecimal avg = BigDecimal.valueOf(starSum + violationPseudoSum)
                    .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);
            if (avg.compareTo(BigDecimal.ZERO) < 0) avg = BigDecimal.ZERO;
            if (avg.compareTo(new BigDecimal("5")) > 0) avg = new BigDecimal("5.00");
            cp.setAvgRating(avg);
        }

        cp.setRatingCount((int) reviewCount);
        creatorProfileRepo.save(cp);
    }
}
