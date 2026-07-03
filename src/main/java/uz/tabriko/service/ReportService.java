package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.Report;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.ReportStatus;
import uz.tabriko.dto.request.CreateReportRequest;
import uz.tabriko.dto.response.PageResponse;
import uz.tabriko.dto.response.ReportResponse;
import uz.tabriko.repository.ReportRepository;
import uz.tabriko.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepo;
    private final UserRepository userRepo;
    private final UserMapper mapper;

    @Transactional
    public ReportResponse createReport(UUID reporterId, CreateReportRequest req) {
        User reporter = userRepo.findById(reporterId)
            .orElseThrow(() -> ApiException.notFound("User not found"));

        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetType(req.getTargetType());
        report.setTargetId(req.getTargetId());
        report.setReason(req.getReason());
        report.setStatus(ReportStatus.OPEN);
        reportRepo.save(report);
        return mapper.toReportResponse(report);
    }

    public PageResponse<ReportResponse> getReports(ReportStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size);
        if (status != null) {
            return PageResponse.of(reportRepo.findByStatusOrderByCreatedAtDesc(status, pageable), mapper::toReportResponse);
        }
        return PageResponse.of(reportRepo.findAllByOrderByCreatedAtDesc(pageable), mapper::toReportResponse);
    }

    @Transactional
    public ReportResponse updateStatus(Long reportId, ReportStatus newStatus) {
        Report report = reportRepo.findById(reportId)
            .orElseThrow(() -> ApiException.notFound("Report not found"));
        report.setStatus(newStatus);
        reportRepo.save(report);
        return mapper.toReportResponse(report);
    }
}
