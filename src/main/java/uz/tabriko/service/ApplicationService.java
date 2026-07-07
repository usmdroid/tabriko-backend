package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.*;
import uz.tabriko.domain.enums.*;
import uz.tabriko.dto.request.AdminApplicationDecisionRequest;
import uz.tabriko.dto.request.ReplyApplicationRequest;
import uz.tabriko.dto.request.SubmitApplicationRequest;
import uz.tabriko.dto.response.ApplicationDetailResponse;
import uz.tabriko.dto.response.ApplicationMessageResponse;
import uz.tabriko.dto.response.ApplicationSubmitResponse;
import uz.tabriko.dto.response.ApplicationVerificationResponse;
import uz.tabriko.dto.response.PageResponse;
import uz.tabriko.dto.response.SampleUploadResponse;
import uz.tabriko.infrastructure.firebase.OtpService;
import uz.tabriko.infrastructure.media.MediaStorageService;
import uz.tabriko.repository.*;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.telegram.repository.TelegramVerificationRepository;

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
            "Ushbu matnni nusxalab, Instagram orqali @tabriko akkauntiga Direct (DM) xabar sifatida yuboring.";

    // Pre-approved verification phrases — one is picked at random per application
    // and must be sent verbatim via Instagram DM to @tabriko so a moderator can
    // match the sender's IG username to this application.
    private static final List<String> IG_VERIFY_PHRASES = List.of(
            "TabrikO orqali sizga chin dildan tabriklar yubormoqchiman!",
            "Hayotingizga yana bir quvonchli lahza — TabrikO bilan!",
            "Kulib yashang, TabrikO doim yoningizda!",
            "Bugun ajoyib kun, TabrikO buni nishonlaydi!",
            "TabrikO — har bir tabrikni maxsus qiladi.",
            "Yulduzlardan tabrik — faqat TabrikO'da!",
            "Baxtli lahzalar TabrikO bilan boshlanadi.",
            "TabrikO sizga ilhom va tabassum ulashadi.",
            "Har bir bayram TabrikO bilan yanada yorqin.",
            "TabrikO — sizning shaxsiy tabrik do'stingiz.",
            "Quvonchni ulashish TabrikO bilan oson.",
            "TabrikO orqali yuragingizdagi so'zlarni ayting."
    );

    private static final long MAX_SAMPLE_VIDEO_BYTES = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_SAMPLE_EXTENSIONS = Set.of("mp4", "mov");

    private final OtpService otpService;
    private final CreatorApplicationRepository applicationRepo;
    private final ApplicationMessageRepository messageRepo;
    private final CategoryRepository categoryRepo;
    private final UserRepository userRepo;
    private final CreatorProfileRepository creatorProfileRepo;
    private final TelegramVerificationRepository telegramVerificationRepo;
    private final MediaStorageService mediaStorage;

    private final SecureRandom secureRandom = new SecureRandom();

    // --- PUBLIC ---

    @Transactional
    public ApplicationSubmitResponse submit(SubmitApplicationRequest req) {
        if (!otpService.verifyOtp(req.getPhone(), req.getCode())) {
            throw ApiException.badRequest("Invalid or expired OTP code");
        }

        if (applicationRepo.existsByPhoneAndStatusIn(req.getPhone(),
                List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW, ApplicationStatus.INFO_REQUESTED))) {
            throw ApiException.conflict("An active application already exists for this phone");
        }

        CreatorApplication app = new CreatorApplication();
        app.setPhone(req.getPhone());
        app.setName(req.getName());
        app.setActivityType(req.getActivityType());
        app.setSocialType(req.getSocialType());
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

        String igVerifyCode = null;
        if (req.getSocialType() == ApplicationSocialType.INSTAGRAM) {
            if (req.getIgUsername() == null || req.getIgUsername().isBlank()) {
                throw ApiException.badRequest("igUsername is required for INSTAGRAM social type");
            }
            app.setIgUsername(req.getIgUsername());
            igVerifyCode = generateIgVerifyCode();
            app.setIgVerifyCode(igVerifyCode);
        } else {
            // TELEGRAM: username is stored for reference; actual verification happens
            // via the bot conversation, linked below by phone if already completed.
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
            creatorProfileRepo.save(profile);
        }

        // Resolve reviewer
        User admin = userRepo.findById(adminPrincipal.getUserId())
                .orElseThrow(() -> ApiException.notFound("Admin user not found"));

        app.setStatus(ApplicationStatus.APPROVED);
        app.setReviewedBy(admin);
        app.setUpdatedAt(Instant.now());
        applicationRepo.save(app);
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
        dto.setSocialType(app.getSocialType().name());
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
        if (app.getSocialType() == ApplicationSocialType.TELEGRAM) {
            ApplicationVerificationResponse.TelegramDetail tg = new ApplicationVerificationResponse.TelegramDetail();
            var tv = app.getTelegramVerification();
            if (tv != null) {
                tg.setVerified("VERIFIED".equals(tv.getStatus()));
                tg.setChannelName(tv.getChatTitle());
                tg.setChannelUsername(tv.getChatUsername());
                tg.setSubscribers(tv.getSubscribers());
                tg.setOwnerStatus(tv.getOwnerStatus());
                tg.setChatType(tv.getChatType());
            }
            // verified=false and all fields null when no linked record
            v.setTelegram(tg);
        } else if (app.getSocialType() == ApplicationSocialType.INSTAGRAM) {
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

    private String generateIgVerifyCode() {
        return IG_VERIFY_PHRASES.get(secureRandom.nextInt(IG_VERIFY_PHRASES.size()));
    }
}
