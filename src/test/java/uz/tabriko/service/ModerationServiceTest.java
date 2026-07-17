package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.CreatorModerationMessage;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.ModerationAuthorRole;
import uz.tabriko.domain.enums.ModerationMessageKind;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.dto.request.AdminSendModerationRequest;
import uz.tabriko.dto.request.CreatorReplyRequest;
import uz.tabriko.dto.response.CreatorModerationThreadResponse;
import uz.tabriko.dto.response.ModerationMessageResponse;
import uz.tabriko.repository.CreatorModerationMessageRepository;
import uz.tabriko.repository.CreatorProfileRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock CreatorModerationMessageRepository moderationRepo;
    @Mock CreatorProfileRepository creatorProfileRepo;
    @Mock NotificationService notificationService;

    @InjectMocks ModerationService moderationService;

    private UUID creatorId;
    private User creatorUser;
    private CreatorProfile creatorProfile;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        creatorUser = new User();
        creatorUser.setId(creatorId);
        creatorUser.setRole(Role.CREATOR);
        creatorUser.setStatus(UserStatus.ACTIVE);

        creatorProfile = new CreatorProfile();
        creatorProfile.setUser(creatorUser);
    }

    // --- Scenario 1: Admin posts WARNING → kind=WARNING, creator status unchanged ---

    @Test
    void adminAppend_warning_savesWithKindWarning_creatorRemainsActive() {
        AdminSendModerationRequest req = new AdminSendModerationRequest();
        req.setKind(ModerationMessageKind.WARNING);
        req.setBody("Please fix your profile photo.");

        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(creatorProfile));
        when(moderationRepo.save(any())).thenAnswer(inv -> {
            CreatorModerationMessage m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });

        ModerationMessageResponse resp = moderationService.adminAppend(creatorId, req, UUID.randomUUID());

        assertThat(resp.getKind()).isEqualTo(ModerationMessageKind.WARNING);
        assertThat(resp.getAuthorRole()).isEqualTo(ModerationAuthorRole.ADMIN);
        assertThat(resp.getBody()).isEqualTo("Please fix your profile photo.");
        // creator user status must remain ACTIVE — WARNING is non-blocking
        assertThat(creatorUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void adminAppend_rejectsSystemKindSuspension_throwsBadRequest() {
        AdminSendModerationRequest req = new AdminSendModerationRequest();
        req.setKind(ModerationMessageKind.SUSPENSION);
        req.setBody("Reason");

        assertThatThrownBy(() -> moderationService.adminAppend(creatorId, req, UUID.randomUUID()))
                .isInstanceOf(ApiException.class);

        verify(moderationRepo, never()).save(any());
    }

    @Test
    void adminAppend_rejectsSystemKindReactivation_throwsBadRequest() {
        AdminSendModerationRequest req = new AdminSendModerationRequest();
        req.setKind(ModerationMessageKind.REACTIVATION);
        req.setBody("Reason");

        assertThatThrownBy(() -> moderationService.adminAppend(creatorId, req, UUID.randomUUID()))
                .isInstanceOf(ApiException.class);

        verify(moderationRepo, never()).save(any());
    }

    // --- Scenario 2: Creator posts reply → authorRole=CREATOR in admin thread ---

    @Test
    void creatorReply_savesWithCreatorAuthorRole() {
        CreatorReplyRequest req = new CreatorReplyRequest();
        req.setBody("I understand, will fix it.");

        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(creatorProfile));
        when(moderationRepo.save(any())).thenAnswer(inv -> {
            CreatorModerationMessage m = inv.getArgument(0);
            m.setId(2L);
            return m;
        });

        ModerationMessageResponse resp = moderationService.creatorReply(creatorId, req);

        assertThat(resp.getAuthorRole()).isEqualTo(ModerationAuthorRole.CREATOR);
        assertThat(resp.getKind()).isEqualTo(ModerationMessageKind.MESSAGE);
        assertThat(resp.getBody()).isEqualTo("I understand, will fix it.");
        assertThat(resp.isReadByCreator()).isTrue();

        // Verify it appears in admin GET thread
        ArgumentCaptor<CreatorModerationMessage> captor = ArgumentCaptor.forClass(CreatorModerationMessage.class);
        verify(moderationRepo).save(captor.capture());
        assertThat(captor.getValue().getAuthorRole()).isEqualTo(ModerationAuthorRole.CREATOR);
    }

    @Test
    void getAdminThread_returnsCreatorReplyWithCreatorRole() {
        CreatorModerationMessage reply = new CreatorModerationMessage();
        reply.setId(10L);
        reply.setCreatorUser(creatorUser);
        reply.setAuthorRole(ModerationAuthorRole.CREATOR);
        reply.setKind(ModerationMessageKind.MESSAGE);
        reply.setBody("My reply");

        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(creatorProfile));
        when(moderationRepo.findByCreatorUserIdOrderByCreatedAtAsc(creatorId)).thenReturn(List.of(reply));

        List<ModerationMessageResponse> thread = moderationService.getAdminThread(creatorId);

        assertThat(thread).hasSize(1);
        assertThat(thread.get(0).getAuthorRole()).isEqualTo(ModerationAuthorRole.CREATOR);
    }

    // --- Scenario 3: Suspend triggers SUSPENSION entry ---

    @Test
    void appendSystemEntry_suspension_persistsSuspensionKind() {
        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(creatorProfile));
        when(moderationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        moderationService.appendSystemEntry(creatorId, ModerationMessageKind.SUSPENSION, "Inappropriate content");

        ArgumentCaptor<CreatorModerationMessage> captor = ArgumentCaptor.forClass(CreatorModerationMessage.class);
        verify(moderationRepo).save(captor.capture());
        CreatorModerationMessage saved = captor.getValue();
        assertThat(saved.getKind()).isEqualTo(ModerationMessageKind.SUSPENSION);
        assertThat(saved.getAuthorRole()).isEqualTo(ModerationAuthorRole.SYSTEM);
        assertThat(saved.getBody()).isEqualTo("Inappropriate content");
    }

    @Test
    void adminService_suspendCreator_callsAppendSystemEntryWithSuspensionKind() {
        // Verify that AdminService hooks into ModerationService on suspend
        ModerationService spyModeration = mock(ModerationService.class);

        uz.tabriko.repository.UserRepository userRepo = mock(uz.tabriko.repository.UserRepository.class);
        uz.tabriko.repository.UserDeviceRepository userDeviceRepo = mock(uz.tabriko.repository.UserDeviceRepository.class);
        uz.tabriko.repository.CategoryRepository categoryRepo = mock(uz.tabriko.repository.CategoryRepository.class);
        uz.tabriko.repository.OrderRepository orderRepo = mock(uz.tabriko.repository.OrderRepository.class);
        uz.tabriko.repository.PortfolioItemRepository portfolioRepo = mock(uz.tabriko.repository.PortfolioItemRepository.class);
        uz.tabriko.repository.ReportRepository reportRepo = mock(uz.tabriko.repository.ReportRepository.class);
        uz.tabriko.repository.PlatformSettingsRepository settingsRepo = mock(uz.tabriko.repository.PlatformSettingsRepository.class);
        uz.tabriko.repository.WalletTransactionRepository walletTxRepo = mock(uz.tabriko.repository.WalletTransactionRepository.class);
        uz.tabriko.repository.CreatorContactRepository contactRepo = mock(uz.tabriko.repository.CreatorContactRepository.class);
        uz.tabriko.repository.CreatorRequisiteRepository creatorRequisiteRepo = mock(uz.tabriko.repository.CreatorRequisiteRepository.class);
        uz.tabriko.infrastructure.payment.PaymentGateway paymentGateway = mock(uz.tabriko.infrastructure.payment.PaymentGateway.class);
        NotificationService notifService = mock(NotificationService.class);
        uz.tabriko.infrastructure.firebase.PushNotificationService pushService = mock(uz.tabriko.infrastructure.firebase.PushNotificationService.class);
        uz.tabriko.infrastructure.media.MediaStorageService mediaService = mock(uz.tabriko.infrastructure.media.MediaStorageService.class);
        UserMapper mapper = mock(UserMapper.class);
        uz.tabriko.repository.CreatorModerationMessageRepository mockModerationRepo = mock(uz.tabriko.repository.CreatorModerationMessageRepository.class);

        AdminService adminSvc = new AdminService(userRepo, userDeviceRepo, creatorProfileRepo, categoryRepo,
                orderRepo, portfolioRepo, reportRepo, settingsRepo, walletTxRepo, contactRepo,
                creatorRequisiteRepo, paymentGateway, notifService, pushService, mediaService, mapper, spyModeration, mockModerationRepo);

        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(creatorProfile));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(creatorProfileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminSvc.suspendCreator(creatorId, "Test reason");

        verify(spyModeration).appendSystemEntry(creatorId, ModerationMessageKind.SUSPENSION, "Test reason");
    }

    // --- Scenario 4: Reactivate triggers REACTIVATION entry ---

    @Test
    void appendSystemEntry_reactivation_persistsReactivationKind() {
        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(creatorProfile));
        when(moderationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        moderationService.appendSystemEntry(creatorId, ModerationMessageKind.REACTIVATION, "Creator reactivated.");

        ArgumentCaptor<CreatorModerationMessage> captor = ArgumentCaptor.forClass(CreatorModerationMessage.class);
        verify(moderationRepo).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(ModerationMessageKind.REACTIVATION);
        assertThat(captor.getValue().getAuthorRole()).isEqualTo(ModerationAuthorRole.SYSTEM);
    }

    @Test
    void adminService_reactivateCreator_callsAppendSystemEntryWithReactivationKind() {
        ModerationService spyModeration = mock(ModerationService.class);

        uz.tabriko.repository.UserRepository userRepo = mock(uz.tabriko.repository.UserRepository.class);
        uz.tabriko.repository.UserDeviceRepository userDeviceRepo = mock(uz.tabriko.repository.UserDeviceRepository.class);
        uz.tabriko.repository.CategoryRepository categoryRepo = mock(uz.tabriko.repository.CategoryRepository.class);
        uz.tabriko.repository.OrderRepository orderRepo = mock(uz.tabriko.repository.OrderRepository.class);
        uz.tabriko.repository.PortfolioItemRepository portfolioRepo = mock(uz.tabriko.repository.PortfolioItemRepository.class);
        uz.tabriko.repository.ReportRepository reportRepo = mock(uz.tabriko.repository.ReportRepository.class);
        uz.tabriko.repository.PlatformSettingsRepository settingsRepo = mock(uz.tabriko.repository.PlatformSettingsRepository.class);
        uz.tabriko.repository.WalletTransactionRepository walletTxRepo = mock(uz.tabriko.repository.WalletTransactionRepository.class);
        uz.tabriko.repository.CreatorContactRepository contactRepo = mock(uz.tabriko.repository.CreatorContactRepository.class);
        uz.tabriko.repository.CreatorRequisiteRepository creatorRequisiteRepo = mock(uz.tabriko.repository.CreatorRequisiteRepository.class);
        uz.tabriko.infrastructure.payment.PaymentGateway paymentGateway = mock(uz.tabriko.infrastructure.payment.PaymentGateway.class);
        NotificationService notifService = mock(NotificationService.class);
        uz.tabriko.infrastructure.firebase.PushNotificationService pushService = mock(uz.tabriko.infrastructure.firebase.PushNotificationService.class);
        uz.tabriko.infrastructure.media.MediaStorageService mediaService = mock(uz.tabriko.infrastructure.media.MediaStorageService.class);
        UserMapper mapper = mock(UserMapper.class);
        uz.tabriko.repository.CreatorModerationMessageRepository mockModerationRepo2 = mock(uz.tabriko.repository.CreatorModerationMessageRepository.class);

        AdminService adminSvc = new AdminService(userRepo, userDeviceRepo, creatorProfileRepo, categoryRepo,
                orderRepo, portfolioRepo, reportRepo, settingsRepo, walletTxRepo, contactRepo,
                creatorRequisiteRepo, paymentGateway, notifService, pushService, mediaService, mapper, spyModeration, mockModerationRepo2);

        creatorUser.setStatus(UserStatus.SUSPENDED);
        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(creatorProfile));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(creatorProfileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminSvc.reactivateCreator(creatorId);

        verify(spyModeration).appendSystemEntry(creatorId, ModerationMessageKind.REACTIVATION, "Creator reactivated.");
    }

    // --- Scenario 5: Self-profile activeWarningCount reflects open warnings ---

    @Test
    void getCreatorThread_activeWarningCount_persistsAfterMarkRead() {
        // Warning starts as unread; after markAdminMessagesRead it becomes readByCreator=true,
        // but activeWarningCount must still reflect it (count is not gated on readByCreator).
        CreatorModerationMessage warning = new CreatorModerationMessage();
        warning.setId(10L);
        warning.setCreatorUser(creatorUser);
        warning.setAuthorRole(ModerationAuthorRole.ADMIN);
        warning.setKind(ModerationMessageKind.WARNING);
        warning.setBody("Fix your requisites");
        warning.setReadByCreator(true); // simulate already-read state

        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(creatorProfile));
        when(moderationRepo.findByCreatorUserIdOrderByCreatedAtAsc(creatorId)).thenReturn(List.of(warning));

        CreatorModerationThreadResponse resp = moderationService.getCreatorThread(creatorId);

        // Even though the warning is already read, activeWarningCount must be 1
        assertThat(resp.getActiveWarningCount()).isEqualTo(1L);
        assertThat(resp.getUnreadCount()).isEqualTo(0L); // read, so unread=0
    }

    @Test
    void getCreatorThread_activeWarningCount_reflectsUnreadWarnings() {
        CreatorModerationMessage warning = new CreatorModerationMessage();
        warning.setId(1L);
        warning.setCreatorUser(creatorUser);
        warning.setAuthorRole(ModerationAuthorRole.ADMIN);
        warning.setKind(ModerationMessageKind.WARNING);
        warning.setBody("Fix your avatar");
        warning.setReadByCreator(false);

        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(creatorProfile));
        when(moderationRepo.findByCreatorUserIdOrderByCreatedAtAsc(creatorId)).thenReturn(List.of(warning));

        CreatorModerationThreadResponse resp = moderationService.getCreatorThread(creatorId);

        assertThat(resp.getActiveWarningCount()).isEqualTo(1L);
        assertThat(resp.getUnreadCount()).isEqualTo(1L);
        assertThat(resp.getMessages()).hasSize(1);
        assertThat(resp.getMessages().get(0).getKind()).isEqualTo(ModerationMessageKind.WARNING);
    }
}
