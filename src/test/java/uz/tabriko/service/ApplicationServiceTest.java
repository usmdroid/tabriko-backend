package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.domain.entity.CreatorApplication;
import uz.tabriko.domain.enums.ApplicationActivityType;
import uz.tabriko.domain.enums.ApplicationSocialType;
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
import uz.tabriko.telegram.repository.TelegramVerificationRepository;
import uz.tabriko.telegram.service.TelegramBotService;

import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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

    ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new ApplicationService(
                otpService, applicationRepo, messageRepo, categoryRepo, userRepo,
                creatorProfileRepo, telegramVerificationRepo, telegramBotService, mediaStorage);
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
}
