package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.CreatorProfile;
import uz.tabriko.domain.entity.PortfolioItem;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.domain.enums.UserStatus;
import uz.tabriko.dto.request.UpdatePayoutRequest;
import uz.tabriko.dto.request.UpdateSocialRequest;
import uz.tabriko.dto.response.CreatorSelfProfileResponse;
import uz.tabriko.infrastructure.media.MediaStorageService;
import uz.tabriko.repository.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorServiceTest {

    @Mock CreatorProfileRepository creatorProfileRepo;
    @Mock PortfolioItemRepository portfolioRepo;
    @Mock CategoryRepository categoryRepo;
    @Mock OrderRepository orderRepo;
    @Mock DeliveryRepository deliveryRepo;
    @Mock MediaStorageService mediaStorage;
    @Mock WalletService walletService;
    @Mock UserMapper mapper;

    @InjectMocks CreatorService creatorService;

    private final UserMapper realMapper = new UserMapper();

    private UUID creatorId;
    private User creatorUser;
    private CreatorProfile creatorProfile;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();

        creatorUser = new User();
        creatorUser.setId(creatorId);
        creatorUser.setName("Test Creator");
        creatorUser.setPhone("+998900000001");
        creatorUser.setRole(Role.CREATOR);
        creatorUser.setStatus(UserStatus.ACTIVE);

        creatorProfile = new CreatorProfile();
        creatorProfile.setUserId(creatorId);
        creatorProfile.setUser(creatorUser);
    }

    // ===== recomputeProfileComplete — 3-condition gate =====

    @Test
    void recomputeProfileComplete_trueWhenAllThreeMet() {
        creatorProfile.setIdDocumentNumber("AA1234567");
        creatorProfile.setIdDocumentUrl("https://cdn/doc.jpg");
        creatorProfile.setPayoutCard("8600123456789012");
        when(portfolioRepo.countByCreatorId(creatorId)).thenReturn(1L);

        creatorService.recomputeProfileComplete(creatorProfile);

        assertThat(creatorProfile.isProfileComplete()).isTrue();
    }

    @Test
    void recomputeProfileComplete_falseWhenIdUrlMissing() {
        creatorProfile.setIdDocumentNumber("AA1234567");
        // idDocumentUrl not set
        creatorProfile.setPayoutCard("8600123456789012");
        when(portfolioRepo.countByCreatorId(creatorId)).thenReturn(1L);

        creatorService.recomputeProfileComplete(creatorProfile);

        assertThat(creatorProfile.isProfileComplete()).isFalse();
    }

    @Test
    void recomputeProfileComplete_falseWhenIdNumberMissing() {
        // idDocumentNumber not set, url set
        creatorProfile.setIdDocumentUrl("https://cdn/doc.jpg");
        creatorProfile.setPayoutCard("8600123456789012");
        when(portfolioRepo.countByCreatorId(creatorId)).thenReturn(1L);

        creatorService.recomputeProfileComplete(creatorProfile);

        assertThat(creatorProfile.isProfileComplete()).isFalse();
    }

    @Test
    void recomputeProfileComplete_falseWhenPayoutMissing() {
        creatorProfile.setIdDocumentNumber("AA1234567");
        creatorProfile.setIdDocumentUrl("https://cdn/doc.jpg");
        when(portfolioRepo.countByCreatorId(creatorId)).thenReturn(1L);

        creatorService.recomputeProfileComplete(creatorProfile);

        assertThat(creatorProfile.isProfileComplete()).isFalse();
    }

    @Test
    void recomputeProfileComplete_falseWhenPortfolioEmpty() {
        creatorProfile.setIdDocumentNumber("AA1234567");
        creatorProfile.setIdDocumentUrl("https://cdn/doc.jpg");
        creatorProfile.setPayoutCard("8600123456789012");
        when(portfolioRepo.countByCreatorId(creatorId)).thenReturn(0L);

        creatorService.recomputeProfileComplete(creatorProfile);

        assertThat(creatorProfile.isProfileComplete()).isFalse();
    }

    @Test
    void recomputeProfileComplete_trueWithPayoutAccountInsteadOfCard() {
        creatorProfile.setIdDocumentNumber("AA1234567");
        creatorProfile.setIdDocumentUrl("https://cdn/doc.jpg");
        creatorProfile.setPayoutAccount("UZ12345678901234567890");
        when(portfolioRepo.countByCreatorId(creatorId)).thenReturn(1L);

        creatorService.recomputeProfileComplete(creatorProfile);

        assertThat(creatorProfile.isProfileComplete()).isTrue();
    }

    // ===== updateKycIdentity =====

    @Test
    void updateKycIdentity_setsNumberAndUrl() {
        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(creatorProfile));
        when(portfolioRepo.countByCreatorId(creatorId)).thenReturn(0L);
        when(creatorProfileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(portfolioRepo.findByCreatorId(creatorId)).thenReturn(List.of());
        when(mapper.toCreatorSelfProfileResponse(any(), any())).thenReturn(new CreatorSelfProfileResponse());

        MockMultipartFile file = new MockMultipartFile("file", "doc.jpg", "image/jpeg", new byte[]{1, 2});
        when(mediaStorage.store(any(), eq("kyc"))).thenReturn("https://cdn/doc.jpg");

        creatorService.updateKycIdentity(creatorId, "AA1234567", file);

        assertThat(creatorProfile.getIdDocumentNumber()).isEqualTo("AA1234567");
        assertThat(creatorProfile.getIdDocumentUrl()).isEqualTo("https://cdn/doc.jpg");
        verify(creatorProfileRepo).save(creatorProfile);
    }

    @Test
    void updateKycIdentity_rejectsBlankNumber() {
        assertThatThrownBy(() -> creatorService.updateKycIdentity(creatorId, "", null))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("idNumber is required");
    }

    @Test
    void updateKycIdentity_rejectsNullNumber() {
        assertThatThrownBy(() -> creatorService.updateKycIdentity(creatorId, null, null))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("idNumber is required");
    }

    // ===== updatePayout =====

    @Test
    void updatePayout_setsCardAndHolder() {
        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(creatorProfile));
        when(portfolioRepo.countByCreatorId(creatorId)).thenReturn(0L);
        when(creatorProfileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(portfolioRepo.findByCreatorId(creatorId)).thenReturn(List.of());
        when(mapper.toCreatorSelfProfileResponse(any(), any())).thenReturn(new CreatorSelfProfileResponse());

        UpdatePayoutRequest req = new UpdatePayoutRequest();
        req.setCard("8600123456789012");
        req.setHolder("John Doe");

        creatorService.updatePayout(creatorId, req);

        assertThat(creatorProfile.getPayoutCard()).isEqualTo("8600123456789012");
        assertThat(creatorProfile.getPayoutHolder()).isEqualTo("John Doe");
        verify(creatorProfileRepo).save(creatorProfile);
    }

    @Test
    void updatePayout_rejectsNullCardAndAccount() {
        UpdatePayoutRequest req = new UpdatePayoutRequest();
        req.setHolder("John Doe");
        // card and account both null

        assertThatThrownBy(() -> creatorService.updatePayout(creatorId, req))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("card or account is required");
    }

    @Test
    void updatePayout_rejectsMissingHolder() {
        UpdatePayoutRequest req = new UpdatePayoutRequest();
        req.setCard("8600123456789012");
        // holder null

        assertThatThrownBy(() -> creatorService.updatePayout(creatorId, req))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("holder is required");
    }

    // ===== updateSocial =====

    @Test
    void updateSocial_setsTelegram() {
        when(creatorProfileRepo.findByUserId(creatorId)).thenReturn(Optional.of(creatorProfile));
        when(creatorProfileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(portfolioRepo.findByCreatorId(creatorId)).thenReturn(List.of());
        when(mapper.toCreatorSelfProfileResponse(any(), any())).thenReturn(new CreatorSelfProfileResponse());

        UpdateSocialRequest req = new UpdateSocialRequest();
        req.setTelegram("@creator");

        creatorService.updateSocial(creatorId, req);

        assertThat(creatorProfile.getSocialTelegram()).isEqualTo("@creator");
    }

    // ===== missingItems list (via real UserMapper — verifies gate criteria) =====

    @Test
    void missingItems_emptyWhenAllThreeMet() {
        creatorProfile.setIdDocumentNumber("AA1234567");
        creatorProfile.setIdDocumentUrl("https://cdn/doc.jpg");
        creatorProfile.setPayoutCard("8600123456789012");
        PortfolioItem item = new PortfolioItem();

        CreatorSelfProfileResponse r = realMapper.toCreatorSelfProfileResponse(creatorProfile, List.of(item));

        assertThat(r.getMissing()).isEmpty();
        assertThat(r.isProfileComplete()).isTrue();
    }

    @Test
    void missingItems_listsPassportWhenNumberMissing() {
        // no idDocumentNumber
        creatorProfile.setIdDocumentUrl("https://cdn/doc.jpg");
        creatorProfile.setPayoutCard("8600123456789012");
        PortfolioItem item = new PortfolioItem();

        CreatorSelfProfileResponse r = realMapper.toCreatorSelfProfileResponse(creatorProfile, List.of(item));

        assertThat(r.getMissing()).contains("passport");
        assertThat(r.isProfileComplete()).isFalse();
    }

    @Test
    void missingItems_listsPassportWhenUrlMissing() {
        creatorProfile.setIdDocumentNumber("AA1234567");
        // no idDocumentUrl
        creatorProfile.setPayoutCard("8600123456789012");
        PortfolioItem item = new PortfolioItem();

        CreatorSelfProfileResponse r = realMapper.toCreatorSelfProfileResponse(creatorProfile, List.of(item));

        assertThat(r.getMissing()).contains("passport");
        assertThat(r.isProfileComplete()).isFalse();
    }

    @Test
    void missingItems_listsPaymentWhenNoPayout() {
        creatorProfile.setIdDocumentNumber("AA1234567");
        creatorProfile.setIdDocumentUrl("https://cdn/doc.jpg");
        // no payout
        PortfolioItem item = new PortfolioItem();

        CreatorSelfProfileResponse r = realMapper.toCreatorSelfProfileResponse(creatorProfile, List.of(item));

        assertThat(r.getMissing()).contains("payment");
        assertThat(r.isProfileComplete()).isFalse();
    }

    @Test
    void missingItems_listsPortfolioWhenEmpty() {
        creatorProfile.setIdDocumentNumber("AA1234567");
        creatorProfile.setIdDocumentUrl("https://cdn/doc.jpg");
        creatorProfile.setPayoutCard("8600123456789012");

        CreatorSelfProfileResponse r = realMapper.toCreatorSelfProfileResponse(creatorProfile, List.of());

        assertThat(r.getMissing()).contains("portfolio");
        assertThat(r.isProfileComplete()).isFalse();
    }

    @Test
    void missingItems_doesNotCheckBioOrSocial() {
        // bio, social, priceFrom, deliveryDays all absent — must NOT appear in missingItems
        creatorProfile.setIdDocumentNumber("AA1234567");
        creatorProfile.setIdDocumentUrl("https://cdn/doc.jpg");
        creatorProfile.setPayoutCard("8600123456789012");
        PortfolioItem item = new PortfolioItem();

        CreatorSelfProfileResponse r = realMapper.toCreatorSelfProfileResponse(creatorProfile, List.of(item));

        assertThat(r.getMissing()).doesNotContain("bio", "priceFrom", "deliveryDays", "social");
        assertThat(r.isProfileComplete()).isTrue();
    }

    // ===== masking =====

    @Test
    void masking_idMaskedCorrectly() {
        creatorProfile.setIdDocumentNumber("AA1234567");

        CreatorSelfProfileResponse r = realMapper.toCreatorSelfProfileResponse(creatorProfile, List.of());

        assertThat(r.getIdDocumentNumberMasked()).endsWith("4567");
        assertThat(r.getIdDocumentNumberMasked()).startsWith("*");
        assertThat(r.isIdProvided()).isTrue();
    }

    @Test
    void masking_cardMaskedCorrectly() {
        creatorProfile.setPayoutCard("8600123456789012");

        CreatorSelfProfileResponse r = realMapper.toCreatorSelfProfileResponse(creatorProfile, List.of());

        assertThat(r.getPayoutCardMasked()).isEqualTo("**** **** **** 9012");
    }

    @Test
    void masking_idNotProvidedWhenNumberNull() {
        CreatorSelfProfileResponse r = realMapper.toCreatorSelfProfileResponse(creatorProfile, List.of());

        assertThat(r.isIdProvided()).isFalse();
        assertThat(r.getIdDocumentNumberMasked()).isNull();
    }
}
