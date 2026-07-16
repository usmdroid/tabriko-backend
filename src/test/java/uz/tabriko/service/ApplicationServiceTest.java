package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.CreatorApplication;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.entity.VerifiedPhoneEntity;
import uz.tabriko.domain.enums.ApplicationActivityType;
import uz.tabriko.domain.enums.ApplicationSocialType;
import uz.tabriko.domain.enums.ApplicationStatus;
import uz.tabriko.dto.request.AdminApplicationDecisionRequest;
import uz.tabriko.dto.request.SubmitApplicationRequest;
import uz.tabriko.dto.request.VerifyPhoneRequest;
import uz.tabriko.dto.response.ApplicationSubmitResponse;
import uz.tabriko.dto.response.PhoneVerifyResponse;
import uz.tabriko.infrastructure.firebase.OtpService;
import uz.tabriko.infrastructure.media.MediaStorageService;
import uz.tabriko.repository.ApplicationMessageRepository;
import uz.tabriko.repository.CategoryRepository;
import uz.tabriko.repository.CreatorApplicationRepository;
import uz.tabriko.repository.CreatorProfileRepository;
import uz.tabriko.repository.UserRepository;
import uz.tabriko.repository.VerifiedPhoneRepository;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.telegram.repository.TelegramVerificationRepository;
import uz.tabriko.telegram.service.TelegramBotService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final Pattern IG_CODE_PATTERN = Pattern.compile("^TBK-[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$");
    private static final String FORBIDDEN_CHARS = "0O1IL";

    @Mock OtpService otpService;
    @Mock CreatorApplicationRepository applicationRepo;
    @Mock ApplicationMessageRepository messageRepo;
    @Mock CategoryRepository categoryRepo;
    @Mock UserRepository userRepo;
    @Mock CreatorProfileRepository creatorProfileRepo;
    @Mock TelegramVerificationRepository telegramVerificationRepo;
    @Mock TelegramBotService telegramBotService;
    @Mock MediaStorageService mediaStorage;
    @Mock VerifiedPhoneRepository verifiedPhoneRepo;

    ApplicationService applicationService;

    // In-memory fake backing the mocked repository, so verifyPhone() -> submit() sequences
    // across two calls behave like the real DB-backed lookup did the in-memory map before.
    private final Map<String, VerifiedPhoneEntity> verifiedPhoneStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        verifiedPhoneStore.clear();
        applicationService = new ApplicationService(
                otpService, applicationRepo, messageRepo, categoryRepo, userRepo,
                creatorProfileRepo, telegramVerificationRepo, telegramBotService, mediaStorage, verifiedPhoneRepo);

        lenient().when(verifiedPhoneRepo.findByPhone(anyString())).thenAnswer(inv ->
                Optional.ofNullable(verifiedPhoneStore.get((String) inv.getArgument(0))));
        lenient().when(verifiedPhoneRepo.save(any())).thenAnswer(inv -> {
            VerifiedPhoneEntity e = inv.getArgument(0);
            verifiedPhoneStore.put(e.getPhone(), e);
            return e;
        });
        lenient().doAnswer(inv -> {
            verifiedPhoneStore.remove((String) inv.getArgument(0));
            return null;
        }).when(verifiedPhoneRepo).deleteByPhone(anyString());
    }

    // ===== generateIgCode() format/alphabet — exercised indirectly via verifyPhone() =====

    @Test
    void verifyPhone_igCode_matchesPatternAndAvoidsAmbiguousChars_acrossManyIterations() {
        when(otpService.verifyOtp(anyString(), anyString())).thenReturn(true);
        when(applicationRepo.existsByIgVerifyCode(anyString())).thenReturn(false);

        for (int i = 0; i < 5000; i++) {
            VerifyPhoneRequest req = new VerifyPhoneRequest();
            req.setPhone("+99890000" + String.format("%04d", i));
            req.setCode("1234");

            PhoneVerifyResponse resp = applicationService.verifyPhone(req);

            assertThat(resp.getIgVerifyCode()).matches(IG_CODE_PATTERN);
            for (char c : FORBIDDEN_CHARS.toCharArray()) {
                assertThat(resp.getIgVerifyCode()).doesNotContain(String.valueOf(c));
            }
        }
    }

    // ===== Contract: verifyPhone() returns a non-null igVerifyCode =====

    @Test
    void verifyPhone_returnsNonNullIgVerifyCode() {
        when(otpService.verifyOtp("+998900000001", "1234")).thenReturn(true);
        when(applicationRepo.existsByIgVerifyCode(anyString())).thenReturn(false);

        VerifyPhoneRequest req = new VerifyPhoneRequest();
        req.setPhone("+998900000001");
        req.setCode("1234");

        PhoneVerifyResponse resp = applicationService.verifyPhone(req);

        assertThat(resp.getIgVerifyCode()).isNotNull();
        assertThat(resp.getVerifyToken()).isNotNull();
    }

    // ===== Contract: submit() persists the server-generated code for INSTAGRAM,
    //       ignoring anything the client could theoretically supply =====

    @Test
    void submit_instagram_persistsServerGeneratedCode_fromVerifyPhone() {
        when(otpService.verifyOtp("+998900000002", "1234")).thenReturn(true);
        when(applicationRepo.existsByIgVerifyCode(anyString())).thenReturn(false);

        VerifyPhoneRequest verifyReq = new VerifyPhoneRequest();
        verifyReq.setPhone("+998900000002");
        verifyReq.setCode("1234");
        PhoneVerifyResponse verifyResp = applicationService.verifyPhone(verifyReq);

        SubmitApplicationRequest submitReq = new SubmitApplicationRequest();
        submitReq.setPhone("+998900000002");
        submitReq.setVerifyToken(verifyResp.getVerifyToken());
        submitReq.setName("Test Applicant");
        submitReq.setActivityType(ApplicationActivityType.OTHER);
        submitReq.setOtherText("blogging");
        submitReq.setPassportSeries("AB");
        submitReq.setPassportNumber("1234567");
        submitReq.setSocialTypes(Set.of(ApplicationSocialType.INSTAGRAM));
        submitReq.setIgUsername("test_ig_user");

        when(applicationRepo.save(org.mockito.ArgumentMatchers.any(CreatorApplication.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ApplicationSubmitResponse resp = applicationService.submit(submitReq);

        assertThat(resp.getIgVerifyCode()).isEqualTo(verifyResp.getIgVerifyCode());

        var appCaptor = org.mockito.ArgumentCaptor.forClass(CreatorApplication.class);
        org.mockito.Mockito.verify(applicationRepo).save(appCaptor.capture());
        assertThat(appCaptor.getValue().getIgVerifyCode()).isEqualTo(verifyResp.getIgVerifyCode());
    }

    // ===== Contract: TELEGRAM submissions succeed without any igVerifyCode involvement =====

    @Test
    void submit_telegram_succeedsWithoutIgVerifyCode() {
        when(otpService.verifyOtp("+998900000003", "1234")).thenReturn(true);
        when(applicationRepo.existsByIgVerifyCode(anyString())).thenReturn(false);

        VerifyPhoneRequest verifyReq = new VerifyPhoneRequest();
        verifyReq.setPhone("+998900000003");
        verifyReq.setCode("1234");
        PhoneVerifyResponse verifyResp = applicationService.verifyPhone(verifyReq);

        SubmitApplicationRequest submitReq = new SubmitApplicationRequest();
        submitReq.setPhone("+998900000003");
        submitReq.setVerifyToken(verifyResp.getVerifyToken());
        submitReq.setName("Test Applicant");
        submitReq.setActivityType(ApplicationActivityType.OTHER);
        submitReq.setOtherText("blogging");
        submitReq.setPassportSeries("AB");
        submitReq.setPassportNumber("1234567");
        submitReq.setSocialTypes(Set.of(ApplicationSocialType.TELEGRAM));
        submitReq.setTelegramUsername("test_channel");

        when(applicationRepo.save(org.mockito.ArgumentMatchers.any(CreatorApplication.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ApplicationSubmitResponse resp = applicationService.submit(submitReq);

        assertThat(resp.getIgVerifyCode()).isNull();
        assertThat(resp.getStatus()).isEqualTo("SUBMITTED");
    }

    // ===== Regression (USM-381): the duplicate-active-application guard must be checked
    //       against the NORMALIZED phone, so re-submitting the same number in a different
    //       raw format is still caught as a duplicate =====

    @Test
    void submit_duplicateGuard_catchesSameNumber_inDifferentRawFormat() {
        when(otpService.verifyOtp(anyString(), anyString())).thenReturn(true);
        when(applicationRepo.existsByIgVerifyCode(anyString())).thenReturn(false);
        // An active application already exists for the normalized form of this phone.
        when(applicationRepo.existsByPhoneAndStatusIn(eq("+998901112233"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(true);

        VerifyPhoneRequest verifyReq = new VerifyPhoneRequest();
        verifyReq.setPhone("998 90 111 22 33");
        verifyReq.setCode("1234");
        PhoneVerifyResponse verifyResp = applicationService.verifyPhone(verifyReq);

        SubmitApplicationRequest submitReq = new SubmitApplicationRequest();
        submitReq.setPhone("998 90 111 22 33");
        submitReq.setVerifyToken(verifyResp.getVerifyToken());
        submitReq.setName("Test Applicant");
        submitReq.setActivityType(ApplicationActivityType.OTHER);
        submitReq.setOtherText("blogging");
        submitReq.setPassportSeries("AB");
        submitReq.setPassportNumber("1234567");
        submitReq.setSocialTypes(Set.of(ApplicationSocialType.TELEGRAM));
        submitReq.setTelegramUsername("test_channel");

        assertThatThrownBy(() -> applicationService.submit(submitReq))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("active application already exists");

        org.mockito.Mockito.verify(applicationRepo, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(CreatorApplication.class));
    }

    // ===== Telegram notifications on status transitions =====

    private CreatorApplication makeApp(String phone, ApplicationStatus status) {
        CreatorApplication app = new CreatorApplication();
        app.setPhone(phone);
        app.setStatus(status);
        return app;
    }

    @Test
    void markUnderReview_notifiesApplicantWithUnderReviewMessage() {
        String phone = "+998901111111";
        UUID id = UUID.randomUUID();
        when(applicationRepo.findById(id)).thenReturn(Optional.of(makeApp(phone, ApplicationStatus.SUBMITTED)));
        when(applicationRepo.save(any(CreatorApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        applicationService.markUnderReview(id);

        verify(telegramBotService).notifyApplicant(eq(phone), contains("ko'rib chiqilmoqda"));
    }

    @Test
    void approve_notifiesApplicantWithApprovedMessage() {
        String phone = "+998902222222";
        UUID id = UUID.randomUUID();
        when(applicationRepo.findById(id)).thenReturn(Optional.of(makeApp(phone, ApplicationStatus.SUBMITTED)));
        when(applicationRepo.save(any(CreatorApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID adminId = UUID.randomUUID();
        User admin = new User();
        admin.setId(adminId);
        admin.setPhone("+998900000000");
        UserPrincipal principal = new UserPrincipal(adminId, "+998900000000", null);
        when(userRepo.findByPhone(phone)).thenReturn(Optional.of(admin));
        when(userRepo.findById(adminId)).thenReturn(Optional.of(admin));
        when(creatorProfileRepo.existsById(any())).thenReturn(true);

        applicationService.approve(id, principal);

        verify(telegramBotService).notifyApplicant(eq(phone), contains("tasdiqlandi"));
    }

    @Test
    void reject_notifiesApplicantWithRejectedMessageAndReason() {
        String phone = "+998903333333";
        UUID id = UUID.randomUUID();
        when(applicationRepo.findById(id)).thenReturn(Optional.of(makeApp(phone, ApplicationStatus.SUBMITTED)));
        when(applicationRepo.save(any(CreatorApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminApplicationDecisionRequest req = new AdminApplicationDecisionRequest();
        req.setMessage("Taqdim etilgan hujjatlar to'liq emas");

        applicationService.reject(id, req);

        verify(telegramBotService).notifyApplicant(eq(phone), contains("rad etildi"));
        verify(telegramBotService).notifyApplicant(eq(phone), contains("Sabab:"));
    }

    @Test
    void reject_withNullPhone_doesNotThrow() {
        UUID id = UUID.randomUUID();
        when(applicationRepo.findById(id)).thenReturn(Optional.of(makeApp(null, ApplicationStatus.SUBMITTED)));
        when(applicationRepo.save(any(CreatorApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminApplicationDecisionRequest req = new AdminApplicationDecisionRequest();
        req.setMessage("some reason");

        // notifyApplicant on TelegramBotService handles null phone — ApplicationService must not NPE
        org.assertj.core.api.Assertions.assertThatCode(() -> applicationService.reject(id, req))
                .doesNotThrowAnyException();
    }
}
