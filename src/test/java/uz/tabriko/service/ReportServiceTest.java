package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.Report;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.ReportStatus;
import uz.tabriko.domain.enums.ReportTargetType;
import uz.tabriko.dto.request.CreateReportRequest;
import uz.tabriko.dto.response.ReportResponse;
import uz.tabriko.repository.ReportRepository;
import uz.tabriko.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportRepository reportRepo;
    @Mock UserRepository userRepo;
    @Mock UserMapper mapper;

    @InjectMocks ReportService reportService;

    private UUID reporterId;
    private User reporter;

    @BeforeEach
    void setUp() {
        reporterId = UUID.randomUUID();
        reporter = new User();
        reporter.setId(reporterId);
    }

    @Test
    void createReport_savesWithOpenStatus() {
        CreateReportRequest req = new CreateReportRequest();
        req.setTargetType(ReportTargetType.USER);
        req.setTargetId(UUID.randomUUID().toString());
        req.setReason("Inappropriate content");

        when(userRepo.findById(reporterId)).thenReturn(Optional.of(reporter));
        when(reportRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toReportResponse(any())).thenReturn(new ReportResponse());

        reportService.createReport(reporterId, req);

        ArgumentCaptor<Report> cap = ArgumentCaptor.forClass(Report.class);
        verify(reportRepo).save(cap.capture());
        Report saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(saved.getReporter().getId()).isEqualTo(reporterId);
        assertThat(saved.getReason()).isEqualTo("Inappropriate content");
    }

    @Test
    void updateStatus_changesReportStatus() {
        Report report = new Report();
        report.setId(1L);
        report.setStatus(ReportStatus.OPEN);

        when(reportRepo.findById(1L)).thenReturn(Optional.of(report));
        when(reportRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toReportResponse(any())).thenReturn(new ReportResponse());

        reportService.updateStatus(1L, ReportStatus.RESOLVED);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        verify(reportRepo).save(report);
    }

    @Test
    void updateStatus_notFoundThrows() {
        when(reportRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.updateStatus(99L, ReportStatus.RESOLVED))
            .isInstanceOf(ApiException.class);
    }
}
