package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.common.util.PhoneUtil;
import uz.tabriko.common.util.PublicCodeUtil;
import uz.tabriko.domain.entity.*;
import uz.tabriko.domain.enums.*;
import uz.tabriko.dto.request.AdminApplicationDecisionRequest;
import uz.tabriko.dto.request.ReplyApplicationRequest;
import uz.tabriko.dto.request.SubmitApplicationRequest;
import uz.tabriko.dto.request.VerifyPhoneRequest;
import uz.tabriko.dto.response.ApplicationDetailResponse;
import uz.tabriko.dto.response.ApplicationMessageResponse;
import uz.tabriko.dto.response.ApplicationSubmitResponse;
import uz.tabriko.dto.response.ApplicationVerificationResponse;
import uz.tabriko.dto.response.PageResponse;
import uz.tabriko.dto.response.PhoneVerifyResponse;
import uz.tabriko.dto.response.SampleUploadResponse;
import uz.tabriko.domain.entity.VerifiedPhoneEntity;
import uz.tabriko.infrastructure.firebase.OtpService;
import uz.tabriko.infrastructure.media.MediaStorageService;
import uz.tabriko.repository.*;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.telegram.enums.TelegramVerificationStatus;
import uz.tabriko.telegram.repository.TelegramVerificationRepository;
import uz.tabriko.telegram.service.TelegramBotService;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final String IG_INSTRUCTIONS =
            "Ushbu kodni nusxalab, Instagram orqali @tabriko akkauntiga Direct (DM) xabar sifatida yuboring.";

    // Alphabet excludes visually ambiguous characters: 0, O, 1, I, L.
    private static final String IG_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int IG_CODE_LENGTH = 6;
    private static final String IG_CODE_PREFIX = "TBK-";

    private static final long MAX_SAMPLE_VIDEO_BYTES = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_SAMPLE_EXTENSIONS = Set.of("mp4", "mov");

    // How long a verifyToken (issued right after OTP confirmation) stays valid while
    // the applicant fills out the rest of the form — much longer than the OTP's own TTL.
    private static final long VERIFY_TOKEN_TTL_SECONDS = 1800; // 30 minutes

    // Statuses in which an application blocks a new submission (and is worth resuming).
    private static final List<ApplicationStatus> ACTIVE_STATUSES = List.of(
            ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW, ApplicationStatus.INFO_REQUESTED);

    private final OtpService otpService;
    private final CreatorApplicationRepository applicationRepo;
    private final ApplicationMessageRepository messageRepo;
    private final CategoryRepository categoryRepo;
    private final UserRepository userRepo;
    private final CreatorProfileRepository creatorProfileRepo;
    private final TelegramVerificationRepository telegramVerificationRepo;
    private final TelegramBotService telegramBotService;
    private final MediaStorageService mediaStorage;
    private final VerifiedPhoneRepository verifiedPhoneRepo;

    private final SecureRandom secureRandom = new SecureRandom();

    // --- PUBLIC ---

    // Verifies the OTP immediately and exchanges it for a longer-lived token, so the
    // applicant isn't racing the OTP's short TTL while filling out the rest of the form.
    @Transactional
    public PhoneVerifyResponse verifyPhone(VerifyPhoneRequest req) {
        if (!otpService.verifyOtp(req.getPhone(), req.getCode())) {
            throw ApiException.badRequest("Invalid or expired OTP code");
        }

        String token = generateTrackingToken();
        String igCode = generateIgCode();

        VerifiedPhoneEntity entity = verifiedPhoneRepo.findByPhone(req.getPhone()).orElseGet(VerifiedPhoneEntity::new);
        entity.setPhone(req.getPhone());
        entity.setVerifyToken(token);
        entity.setIgVerifyCode(igCode);
        entity.setExpiresAt(Instant.now().plusSeconds(VERIFY_TOKEN_TTL_SECONDS));
        verifiedPhoneRepo.save(entity);

        PhoneVerifyResponse resp = new PhoneVerifyResponse();
        resp.setPhone(req.getPhone());
        resp.setVerifyToken(token);
        resp.setIgVerifyCode(igCode);

        // If an active application already exists, surface its tracking token so the
        // client can jump straight to the status page instead of a blocked new form.
        applicationRepo.findFirstByPhoneOrderByCreatedAtDesc(req.getPhone())
                .filter(a -> ACTIVE_STATUSES.contains(a.getStatus()))
                .ifPresent(a -> {
                    resp.setExistingApplicationId(a.getId());
                    resp.setExistingTrackingToken(a.getTrackingToken());
                });
        return resp;
    }

    @Transactional(readOnly = true)
    public boolean existsActiveByPhone(String phone) {
        return applicationRepo.existsByPhoneAndStatusIn(phone, ACTIVE_STATUSES);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    void evictExpiredVerifications() {
        verifiedPhoneRepo.deleteExpired(Instant.now());
    }

    @Transactional
    public ApplicationSubmitResponse submit(SubmitApplicationRequest req) {
        VerifiedPhoneEntity verified = verifiedPhoneRepo.findByPhone(req.getPhone())
                .filter(v -> !v.isExpired() && v.getVerifyToken().equals(req.getVerifyToken()))
                .orElseThrow(() -> ApiException.badRequest("Phone verification expired. Please verify your phone number again."));
        verifiedPhoneRepo.deleteByPhone(req.getPhone());

        String normalizedPhone = PhoneUtil.normalize(req.getPhone());
        if (applicationRepo.existsByPhoneAndStatusIn(normalizedPhone, ACTIVE_STATUSES)) {
            throw ApiException.conflict("An active application already exists for this phone");
        }

        CreatorApplication app = new CreatorApplication();
        app.setPhone(normalizedPhone);
        app.setName(req.getName());
        app.setActivityType(req.getActivityType());
        app.setPassportSeries(req.getPassportSeries().toUpperCase());
        app.setPassportNumber(req.getPassportNumber());
        app.setSocialTypes(req.getSocialTypes());
        app.setSampleVideoUrl(req.getSampleVideoUrl());
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setTrackingToken(generateTrackingToken());
        app.setCreatedAt(Instant.now());
        app.setUpdatedAt(Instant.now());

        if (req.getActivityType() == ApplicationActivityType.CATEGORY) {
            if (req.getCategoryId() == null) {
                throw ApiException.badRequest("categoryId is required for CATEGORY activity type");
            }
            Category cat = categoryRepo.findById(req.getCategoryId())
                    .orElseThrow(() -> ApiException.badRequest("Category not found"));
            app.setCategory(cat);
        } else {
            if (req.getOtherText() == null || req.getOtherText().isBlank()) {
                throw ApiException.badRequest("otherText is required for OTHER activity type");
            }
            app.setOtherText(req.getOtherText());
        }

        // A creator may select both networks; each is handled independently.
        String igVerifyCode = null;
        if (req.getSocialTypes().contains(ApplicationSocialType.INSTAGRAM)) {
            if (req.getIgUsername() == null || req.getIgUsername().isBlank()) {
                throw ApiException.badRequest("igUsername is required when INSTAGRAM is selected");
            }
            app.setIgUsername(req.getIgUsername());
            igVerifyCode = verified.getIgVerifyCode();
            app.setIgVerifyCode(igVerifyCode);
        }
        if (req.getSocialTypes().contains(ApplicationSocialType.TELEGRAM)) {
            // Username is stored for reference; actual verification happens via the bot
            // conversation, linked below by phone if already completed.
            app.setTelegramUsername(req.getTelegramUsername());
            telegramVerificationRepo.findFirstByPhoneOrderByCreatedAtDesc(req.getPhone())
                    .ifPresent(app::setTelegramVerification);
        }

        applicationRepo.save(app);

        ApplicationSubmitResponse resp = new ApplicationSubmitResponse();
        resp.setApplicationId(app.getId());
        resp.setTrackingToken(app.getTrackingToken());
        resp.setStatus(app.getStatus().name());
        resp.setIgVerifyCode(igVerifyCode);
        resp.setIgInstructions(igVerifyCode != null ? IG_INSTRUCTIONS : null);
        return resp;
    }

    public SampleUploadResponse uploadSample(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File is required");
        }
        if (file.getSize() > MAX_SAMPLE_VIDEO_BYTES) {
            throw ApiException.badRequest("File size exceeds 50MB limit");
        }
        String name = file.getOriginalFilename();
        String ext = (name != null && name.contains("."))
                ? name.substring(name.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_SAMPLE_EXTENSIONS.contains(ext)) {
            throw ApiException.badRequest("Unsupported file type. Allowed: mp4, mov");
        }

        String url = mediaStorage.store(file, "applications");
        SampleUploadResponse resp = new SampleUploadResponse();
        resp.setUrl(url);
        return resp;
    }

    @Transactional(readOnly = true)
    public ApplicationDetailResponse getByToken(UUID id, String token) {
        CreatorApplication app = applicationRepo.findByIdAndTrackingToken(id, token)
                .orElseThrow(() -> ApiException.forbidden("Application not found or token mismatch"));
        return toDetailResponse(app, true);
    }

    @Transactional
    public void replyAsApplicant(UUID id, String token, ReplyApplicationRequest req) {
        CreatorApplication app = applicationRepo.findByIdAndTrackingToken(id, token)
                .orElseThrow(() -> ApiException.forbidden("Application not found or token mismatch"));

        if (app.getStatus() != ApplicationStatus.INFO_REQUESTED) {
            throw ApiException.badRequest("Replies are only allowed when status is INFO_REQUESTED");
        }

        ApplicationMessage msg = new ApplicationMessage();
        msg.setApplication(app);
        msg.setAuthor(MessageAuthor.APPLICANT);
        msg.setText(req.getText());
        msg.setFileUrl(req.getFileUrl());
        msg.setCreatedAt(Instant.now());
        messageRepo.save(msg);
    }

    // --- ADMIN ---

    @Transactional(readOnly = true)
    public PageResponse<ApplicationDetailResponse> listAdmin(String statusStr, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<CreatorApplication> pageResult;
        if (statusStr != null && !statusStr.isBlank()) {
            ApplicationStatus status;
            try {
                status = ApplicationStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Unknown status: " + statusStr);
            }
            pageResult = applicationRepo.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            pageResult = applicationRepo.findAllByOrderByCreatedAtDesc(pageable);
        }
        return PageResponse.of(pageResult, app -> toDetailResponse(app, false));
    }

    @Transactional(readOnly = true)
    public ApplicationDetailResponse getAdminDetail(UUID id) {
        CreatorApplication app = applicationRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Application not found"));
        return toDetailResponse(app, false);
    }

    @Transactional
    public void markUnderReview(UUID id) {
        CreatorApplication app = requireApplication(id);
        if (app.getStatus() == ApplicationStatus.APPROVED) {
            throw ApiException.conflict("Cannot modify an already approved application");
        }
        app.setStatus(ApplicationStatus.UNDER_REVIEW);
        app.setUpdatedAt(Instant.now());
        applicationRepo.save(app);
        telegramBotService.notifyApplicant(app.getPhone(),
                "[Avtomatik xabar, javob bermang]\nArizangiz ko'rib chiqilmoqda");
    }

    @Transactional
    public void requestInfo(UUID id, AdminApplicationDecisionRequest req) {
        CreatorApplication app = requireApplication(id);
        if (app.getStatus() == ApplicationStatus.APPROVED) {
            throw ApiException.conflict("Cannot modify an already approved application");
        }
        app.setStatus(ApplicationStatus.INFO_REQUESTED);
        app.setUpdatedAt(Instant.now());
        applicationRepo.save(app);

        ApplicationMessage msg = new ApplicationMessage();
        msg.setApplication(app);
        msg.setAuthor(MessageAuthor.MODERATOR);
        msg.setText(req.getMessage());
        msg.setCreatedAt(Instant.now());
        messageRepo.save(msg);

        // Deliver the request to the applicant via the Telegram bot (best-effort).
        telegramBotService.notifyApplicant(app.getPhone(), req.getMessage());
    }

    @Transactional
    public void reject(UUID id, AdminApplicationDecisionRequest req) {
        CreatorApplication app = requireApplication(id);
        if (app.getStatus() == ApplicationStatus.APPROVED) {
            throw ApiException.conflict("Cannot modify an already approved application");
        }
        app.setStatus(ApplicationStatus.REJECTED);
        app.setDecisionReason(req.getMessage());
        app.setUpdatedAt(Instant.now());
        applicationRepo.save(app);
        String msg = "[Avtomatik xabar, javob bermang]\nAfsuski, arizangiz rad etildi";
        if (req.getMessage() != null && !req.getMessage().isBlank()) {
            msg += "\nSabab: " + req.getMessage();
        }
        telegramBotService.notifyApplicant(app.getPhone(), msg);
    }

    @Transactional
    public void confirmInstagram(UUID id) {
        CreatorApplication app = requireApplication(id);
        app.setIgOwnershipConfirmed(true);
        app.setUpdatedAt(Instant.now());
        applicationRepo.save(app);
    }

    @Transactional
    public void sendModeratorMessage(UUID id, ReplyApplicationRequest req) {
        CreatorApplication app = requireApplication(id);

        ApplicationMessage msg = new ApplicationMessage();
        msg.setApplication(app);
        msg.setAuthor(MessageAuthor.MODERATOR);
        msg.setText(req.getText());
        msg.setFileUrl(req.getFileUrl());
        msg.setCreatedAt(Instant.now());
        messageRepo.save(msg);

        // Deliver the message to the applicant via the Telegram bot (best-effort).
        telegramBotService.notifyApplicant(app.getPhone(), req.getText());
    }

    @Transactional
    public void deleteApplication(UUID id) {
        CreatorApplication app = requireApplication(id);
        // Remove the message thread first (FK application_id is NOT NULL).
        messageRepo.deleteByApplication(app);
        applicationRepo.delete(app);
    }

    @Transactional
    public void approve(UUID id, UserPrincipal adminPrincipal) {
        CreatorApplication app = requireApplication(id);

        if (app.getStatus() == ApplicationStatus.APPROVED) {
            throw ApiException.conflict("Application is already approved");
        }

        // Find or create user
        User user = userRepo.findByPhone(app.getPhone()).orElseGet(() -> {
            User newUser = new User();
            newUser.setPhone(app.getPhone());
            newUser.setName(app.getName());
            newUser.setRole(Role.CREATOR);
            newUser.setStatus(UserStatus.ACTIVE);
            newUser.setCreatedAt(Instant.now());
            return userRepo.save(newUser);
        });

        // Upgrade role if CLIENT; never downgrade SUPERADMIN/MODERATOR
        if (user.getRole() == Role.CLIENT) {
            user.setRole(Role.CREATOR);
            userRepo.save(user);
        }

        // Create CreatorProfile if missing
        if (!creatorProfileRepo.existsById(user.getId())) {
            CreatorProfile profile = new CreatorProfile();
            profile.setUser(user);
            if (app.getActivityType() == ApplicationActivityType.CATEGORY && app.getCategory() != null) {
                profile.setCategory(app.getCategory());
            }
            profile.setProfileComplete(false);
            profile.setTier(CreatorTier.STANDARD);
            String code;
            do {
                code = PublicCodeUtil.generate();
            } while (creatorProfileRepo.existsByPublicCode(code));
            profile.setPublicCode(code);
            creatorProfileRepo.save(profile);
        }

        // Resolve reviewer
        User admin = userRepo.findById(adminPrincipal.getUserId())
                .orElseThrow(() -> ApiException.notFound("Admin user not found"));

        app.setStatus(ApplicationStatus.APPROVED);
        app.setReviewedBy(admin);
        app.setUpdatedAt(Instant.now());
        applicationRepo.save(app);
        telegramBotService.notifyApplicant(app.getPhone(),
                "[Avtomatik xabar, javob bermang]\nTabriklaymiz! Arizangiz tasdiqlandi");
    }

    // --- Helpers ---

    private CreatorApplication requireApplication(UUID id) {
        return applicationRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Application not found"));
    }

    private ApplicationDetailResponse toDetailResponse(CreatorApplication app, boolean includeToken) {
        ApplicationDetailResponse dto = new ApplicationDetailResponse();
        dto.setId(app.getId());
        dto.setPhone(app.getPhone());
        dto.setName(app.getName());
        dto.setActivityType(app.getActivityType().name());
        if (app.getCategory() != null) {
            dto.setCategoryId(app.getCategory().getId());
            dto.setCategoryName(app.getCategory().getName());
        }
        dto.setOtherText(app.getOtherText());
        dto.setPassportSeries(app.getPassportSeries());
        dto.setPassportNumber(app.getPassportNumber());
        dto.setSocialTypes(app.getSocialTypes().stream().map(Enum::name).sorted().toList());
        dto.setIgUsername(app.getIgUsername());
        dto.setIgVerifyCode(app.getIgVerifyCode());
        dto.setIgOwnershipConfirmed(app.isIgOwnershipConfirmed());
        dto.setTelegramUsername(app.getTelegramUsername());
        if (app.getTelegramVerification() != null) {
            dto.setTelegramVerificationId(app.getTelegramVerification().getId());
        }
        dto.setVerification(buildVerification(app));
        dto.setSampleVideoUrl(app.getSampleVideoUrl());
        dto.setStatus(app.getStatus().name());
        dto.setDecisionReason(app.getDecisionReason());
        dto.setTrackingToken(includeToken ? app.getTrackingToken() : null);
        dto.setCreatedAt(app.getCreatedAt());
        dto.setUpdatedAt(app.getUpdatedAt());

        List<ApplicationMessage> messages = messageRepo.findByApplicationOrderByCreatedAtAsc(app);
        dto.setMessages(messages.stream().map(this::toMessageResponse).collect(Collectors.toList()));
        return dto;
    }

    private ApplicationMessageResponse toMessageResponse(ApplicationMessage msg) {
        ApplicationMessageResponse dto = new ApplicationMessageResponse();
        dto.setId(msg.getId());
        dto.setAuthor(msg.getAuthor().name());
        dto.setText(msg.getText());
        dto.setFileUrl(msg.getFileUrl());
        dto.setCreatedAt(msg.getCreatedAt());
        return dto;
    }

    private ApplicationVerificationResponse buildVerification(CreatorApplication app) {
        ApplicationVerificationResponse v = new ApplicationVerificationResponse();
        // Both networks are independent — populate whichever the applicant selected.
        if (app.getSocialTypes().contains(ApplicationSocialType.TELEGRAM)) {
            ApplicationVerificationResponse.TelegramDetail tg = new ApplicationVerificationResponse.TelegramDetail();
            var tv = app.getTelegramVerification();
            // The bot completes verification AFTER the application is submitted, so the
            // stored link may be null — fall back to the latest record for this phone.
            if (tv == null) {
                tv = telegramVerificationRepo.findFirstByPhoneOrderByCreatedAtDesc(app.getPhone()).orElse(null);
            }
            if (tv != null) {
                tg.setVerified(tv.getStatus() == TelegramVerificationStatus.VERIFIED);
                tg.setChannelName(tv.getChatTitle());
                tg.setChannelUsername(tv.getChatUsername());
                tg.setSubscribers(tv.getSubscribers());
                tg.setOwnerStatus(tv.getOwnerStatus() != null ? tv.getOwnerStatus().name() : null);
                tg.setChatType(tv.getChatType() != null ? tv.getChatType().name() : null);
            }
            // verified=false and all fields null when no linked record
            v.setTelegram(tg);
        }
        if (app.getSocialTypes().contains(ApplicationSocialType.INSTAGRAM)) {
            ApplicationVerificationResponse.InstagramDetail ig = new ApplicationVerificationResponse.InstagramDetail();
            ig.setUsername(app.getIgUsername());
            ig.setVerifyCode(app.getIgVerifyCode());
            ig.setOwnershipConfirmed(app.isIgOwnershipConfirmed());
            v.setInstagram(ig);
        }
        return v;
    }

    private String generateTrackingToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateIgCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(IG_CODE_PREFIX);
            for (int i = 0; i < IG_CODE_LENGTH; i++) {
                sb.append(IG_CODE_ALPHABET.charAt(secureRandom.nextInt(IG_CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (applicationRepo.existsByIgVerifyCode(code));
        return code;
    }
}
