package uz.tabriko.infrastructure.firebase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.domain.entity.OtpCode;
import uz.tabriko.repository.OtpCodeRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecureOtpServiceTest {

    @Mock OtpCodeRepository otpCodeRepo;

    @InjectMocks SecureOtpService otpService;

    private static final String PHONE = "+998901234567";

    private OtpCode existing;

    @BeforeEach
    void setUp() {
        existing = new OtpCode();
        existing.setPhone(PHONE);
    }

    @Test
    void verifyOtp_rejectsHardcodedBackdoorCode_whenNoOtpWasSent() {
        when(otpCodeRepo.findByPhone(PHONE)).thenReturn(Optional.empty());

        assertThat(otpService.verifyOtp(PHONE, "2580")).isFalse();
    }

    @Test
    void sendOtp_storesGeneratedCodeInRepository() {
        when(otpCodeRepo.findByPhone(PHONE)).thenReturn(Optional.empty());
        when(otpCodeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        otpService.sendOtp(PHONE);

        ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepo).save(captor.capture());
        OtpCode saved = captor.getValue();

        assertThat(saved.getPhone()).isEqualTo(PHONE);
        assertThat(saved.getCode()).matches("\\d{4}");
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void verifyOtp_rejectsHardcodedBackdoorCode_evenWithPendingRealOtp() {
        existing.setCode("1234");
        existing.setExpiresAt(Instant.now().plusSeconds(60));
        when(otpCodeRepo.findByPhone(PHONE)).thenReturn(Optional.of(existing));

        if (!"2580".equals(existing.getCode())) {
            assertThat(otpService.verifyOtp(PHONE, "2580")).isFalse();
        }
    }

    @Test
    void verifyOtp_wrongCode_rejectedAndAttemptsIncremented() {
        existing.setCode("1234");
        existing.setExpiresAt(Instant.now().plusSeconds(60));
        existing.setAttempts(0);
        when(otpCodeRepo.findByPhone(PHONE)).thenReturn(Optional.of(existing));

        assertThat(otpService.verifyOtp(PHONE, "0000")).isFalse();

        ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepo).save(captor.capture());
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
        verify(otpCodeRepo, never()).delete(any());
    }

    @Test
    void verifyOtp_correctCode_returnsTrue_andDeletesEntry() {
        existing.setCode("1234");
        existing.setExpiresAt(Instant.now().plusSeconds(60));
        when(otpCodeRepo.findByPhone(PHONE)).thenReturn(Optional.of(existing));

        assertThat(otpService.verifyOtp(PHONE, "1234")).isTrue();

        verify(otpCodeRepo).delete(existing);
        verify(otpCodeRepo, never()).save(any());
    }

    @Test
    void verifyOtp_expiredEntry_returnsFalse_andDeletesEntry() {
        existing.setCode("1234");
        existing.setExpiresAt(Instant.now().minusSeconds(1));
        when(otpCodeRepo.findByPhone(PHONE)).thenReturn(Optional.of(existing));

        assertThat(otpService.verifyOtp(PHONE, "1234")).isFalse();

        verify(otpCodeRepo).delete(existing);
    }

    @Test
    void verifyOtp_exceedsMaxAttempts_returnsFalse_andDeletesEntry() {
        existing.setCode("1234");
        existing.setExpiresAt(Instant.now().plusSeconds(60));
        existing.setAttempts(5); // already at MAX_ATTEMPTS; next attempt (6) exceeds it
        when(otpCodeRepo.findByPhone(PHONE)).thenReturn(Optional.of(existing));

        assertThat(otpService.verifyOtp(PHONE, "1234")).isFalse();

        verify(otpCodeRepo).delete(existing);
        verify(otpCodeRepo, never()).save(any());
    }
}
