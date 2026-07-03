package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.User;
import uz.tabriko.dto.request.UpdateProfileRequest;
import uz.tabriko.dto.response.UserResponse;
import uz.tabriko.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final UserMapper userMapper;

    public UserResponse getMe(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateMe(UUID userId, UpdateProfileRequest req) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (req.getName() != null) user.setName(req.getName());
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        return userMapper.toResponse(userRepo.save(user));
    }

    @Transactional
    public void registerFcmToken(UUID userId, String token) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setFcmToken(token);
        userRepo.save(user);
    }

    public User getOrThrow(UUID userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
