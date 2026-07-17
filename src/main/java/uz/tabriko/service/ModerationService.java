package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.CreatorModerationMessage;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.enums.ModerationAuthorRole;
import uz.tabriko.domain.enums.ModerationMessageKind;
import uz.tabriko.domain.enums.NotificationType;
import uz.tabriko.dto.request.AdminSendModerationRequest;
import uz.tabriko.dto.request.CreatorReplyRequest;
import uz.tabriko.dto.response.CreatorModerationThreadResponse;
import uz.tabriko.dto.response.ModerationMessageResponse;
import uz.tabriko.repository.CreatorModerationMessageRepository;
import uz.tabriko.repository.CreatorProfileRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    private final CreatorModerationMessageRepository moderationRepo;
    private final CreatorProfileRepository creatorProfileRepo;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ModerationMessageResponse> getAdminThread(UUID creatorUserId) {
        creatorProfileRepo.findByUserId(creatorUserId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        return moderationRepo.findByCreatorUserIdOrderByCreatedAtAsc(creatorUserId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ModerationMessageResponse adminAppend(UUID creatorUserId, AdminSendModerationRequest req, UUID adminUserId) {
        if (req.getKind() == ModerationMessageKind.SUSPENSION || req.getKind() == ModerationMessageKind.REACTIVATION) {
            throw ApiException.badRequest("Admin can only send MESSAGE or WARNING");
        }
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorUserId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));

        CreatorModerationMessage msg = new CreatorModerationMessage();
        msg.setCreatorUser(cp.getUser());
        msg.setAuthorRole(ModerationAuthorRole.ADMIN);
        msg.setKind(req.getKind());
        msg.setBody(req.getBody());
        CreatorModerationMessage saved = moderationRepo.save(msg);

        try {
            String title = req.getKind() == ModerationMessageKind.WARNING
                    ? "Warning from admin"
                    : "Message from admin";
            notificationService.sendNotification(creatorUserId, title, req.getBody(), NotificationType.SYSTEM);
        } catch (Exception e) {
            log.warn("[MODERATION] Push notification failed for creator {}: {}", creatorUserId, e.getMessage());
        }

        return toResponse(saved);
    }

    @Transactional
    public void appendSystemEntry(UUID creatorUserId, ModerationMessageKind kind, String body) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorUserId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        CreatorModerationMessage msg = new CreatorModerationMessage();
        msg.setCreatorUser(cp.getUser());
        msg.setAuthorRole(ModerationAuthorRole.SYSTEM);
        msg.setKind(kind);
        msg.setBody(body);
        moderationRepo.save(msg);
    }

    @Transactional
    public CreatorModerationThreadResponse getCreatorThread(UUID creatorUserId) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorUserId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));

        List<CreatorModerationMessage> rawMessages =
                moderationRepo.findByCreatorUserIdOrderByCreatedAtAsc(creatorUserId);

        long unreadCount = rawMessages.stream()
                .filter(m -> !m.isReadByCreator() && m.getAuthorRole() != ModerationAuthorRole.CREATOR)
                .count();
        long activeWarningCount = rawMessages.stream()
                .filter(m -> m.getKind() == ModerationMessageKind.WARNING)
                .count();

        moderationRepo.markAdminMessagesRead(creatorUserId);

        CreatorModerationThreadResponse resp = new CreatorModerationThreadResponse();
        resp.setMessages(rawMessages.stream().map(this::toResponse).collect(Collectors.toList()));
        resp.setStatus(cp.getUser().getStatus());
        resp.setSuspensionReason(cp.getSuspensionReason());
        resp.setUnreadCount(unreadCount);
        resp.setActiveWarningCount(activeWarningCount);
        return resp;
    }

    @Transactional
    public ModerationMessageResponse creatorReply(UUID creatorUserId, CreatorReplyRequest req) {
        CreatorProfile cp = creatorProfileRepo.findByUserId(creatorUserId)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));

        CreatorModerationMessage msg = new CreatorModerationMessage();
        msg.setCreatorUser(cp.getUser());
        msg.setAuthorRole(ModerationAuthorRole.CREATOR);
        msg.setKind(ModerationMessageKind.MESSAGE);
        msg.setBody(req.getBody());
        msg.setReadByCreator(true);
        return toResponse(moderationRepo.save(msg));
    }

    private ModerationMessageResponse toResponse(CreatorModerationMessage msg) {
        ModerationMessageResponse r = new ModerationMessageResponse();
        r.setId(msg.getId());
        r.setAuthorRole(msg.getAuthorRole());
        r.setKind(msg.getKind());
        r.setBody(msg.getBody());
        r.setCreatedAt(msg.getCreatedAt());
        r.setReadByCreator(msg.isReadByCreator());
        return r;
    }
}
