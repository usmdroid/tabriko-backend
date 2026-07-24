package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.dto.response.BirthdayMatchResponse;
import uz.tabriko.infrastructure.media.MediaStorageService;
import uz.tabriko.repository.CreatorProfileRepository;
import uz.tabriko.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContactsService {

    private final UserRepository userRepo;
    private final CreatorProfileRepository creatorProfileRepo;
    private final MediaStorageService mediaStorage;

    public List<BirthdayMatchResponse> findBirthdayMatches(UUID callerId, List<String> hashes) {
        List<User> matched = userRepo.findBirthdayMatches(hashes, callerId);

        List<UUID> creatorIds = matched.stream()
                .filter(u -> u.getRole() == Role.CREATOR)
                .map(User::getId)
                .collect(Collectors.toList());

        Map<UUID, CreatorProfile> cpMap = creatorProfileRepo.findAllById(creatorIds).stream()
                .collect(Collectors.toMap(CreatorProfile::getUserId, Function.identity()));

        return matched.stream().map(u -> toResponse(u, cpMap)).collect(Collectors.toList());
    }

    private BirthdayMatchResponse toResponse(User u, Map<UUID, CreatorProfile> cpMap) {
        BirthdayMatchResponse r = new BirthdayMatchResponse();
        r.setUserId(u.getId());
        r.setHash(u.getPhoneHash());
        r.setName(u.getName());
        r.setAvatarUrl(mediaStorage.publicUrl(u.getAvatarUrl()));
        r.setBirthDay(u.getBirthDate().getDayOfMonth());
        r.setBirthMonth(u.getBirthDate().getMonthValue());
        boolean creator = u.getRole() == Role.CREATOR;
        r.setCreator(creator);
        r.setPublicCode(creator ? Optional.ofNullable(cpMap.get(u.getId()))
                .map(CreatorProfile::getPublicCode).orElse(null) : null);
        return r;
    }
}
