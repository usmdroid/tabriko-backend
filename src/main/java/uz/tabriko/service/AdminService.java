package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.Category;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.dto.request.AddCreatorRequest;
import uz.tabriko.dto.response.CreatorResponse;
import uz.tabriko.dto.response.OrderResponse;
import uz.tabriko.dto.response.PageResponse;
import uz.tabriko.repository.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepo;
    private final CreatorProfileRepository creatorProfileRepo;
    private final CategoryRepository categoryRepo;
    private final OrderRepository orderRepo;
    private final PortfolioItemRepository portfolioRepo;
    private final UserMapper mapper;

    @Transactional
    public CreatorResponse addCreator(AddCreatorRequest req) {
        User user = userRepo.findByPhone(req.getPhone()).orElseGet(() -> {
            User u = new User();
            u.setPhone(req.getPhone());
            u.setName(req.getName());
            u.setRole(Role.CREATOR);
            u.setStatus(UserStatus.ACTIVE);
            return userRepo.save(u);
        });
        user.setRole(Role.CREATOR);
        userRepo.save(user);

        Category category = categoryRepo.findById(req.getCategoryId())
                .orElseThrow(() -> ApiException.notFound("Category not found"));

        CreatorProfile cp = creatorProfileRepo.findByUserId(user.getId()).orElse(new CreatorProfile());
        cp.setUser(user);
        cp.setCategory(category);
        cp.setBio(req.getBio());
        cp.setPriceFrom(req.getPriceFrom() != null ? req.getPriceFrom() : BigDecimal.ZERO);
        cp.setDeliveryDays(req.getDeliveryDays());
        cp.setVerified(false);
        creatorProfileRepo.save(cp);

        return mapper.toCreatorResponse(cp, portfolioRepo.findPublicWithConsent(user.getId()));
    }

    @Transactional
    public CreatorResponse verifyCreator(UUID creatorId) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        cp.setVerified(true);
        creatorProfileRepo.save(cp);
        return mapper.toCreatorResponse(cp, portfolioRepo.findPublicWithConsent(creatorId));
    }

    public List<CreatorResponse> getAllCreators() {
        return creatorProfileRepo.findAll().stream()
                .map(cp -> mapper.toCreatorResponse(cp, portfolioRepo.findPublicWithConsent(cp.getUserId())))
                .collect(Collectors.toList());
    }

    public PageResponse<OrderResponse> getAllOrders(int page, int size) {
        return PageResponse.of(
                orderRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)),
                o -> mapper.toOrderResponse(o, null)
        );
    }
}
