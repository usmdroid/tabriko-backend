package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.tabriko.domain.entity.CrashReport;
import uz.tabriko.dto.request.DiagnosticsReportRequest;
import uz.tabriko.repository.CrashReportRepository;
import uz.tabriko.telegram.service.DiagnosticsTelegramService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiagnosticsService {

    private static final int MAX_STACK_TRACE_LEN = 6000;

    private final CrashReportRepository crashReportRepository;
    private final DiagnosticsTelegramService diagnosticsTelegramService;

    public void report(DiagnosticsReportRequest req, UUID userId) {
        String stackTrace = req.getStackTrace();
        if (stackTrace != null && stackTrace.length() > MAX_STACK_TRACE_LEN) {
            stackTrace = stackTrace.substring(0, MAX_STACK_TRACE_LEN);
        }

        CrashReport entity = new CrashReport();
        entity.setLevel(req.getLevel());
        entity.setMessage(req.getMessage());
        entity.setStackTrace(stackTrace);
        entity.setPlatform(req.getPlatform());
        entity.setAppVersion(req.getAppVersion());
        entity.setOsVersion(req.getOsVersion());
        entity.setDeviceModel(req.getDeviceModel());
        entity.setDeviceId(req.getDeviceId());
        entity.setScreen(req.getScreen());
        entity.setOccurredAt(req.getOccurredAt());
        entity.setUserId(userId);

        CrashReport saved = crashReportRepository.save(entity);

        if ("CRITICAL".equals(req.getLevel()) || "ERROR".equals(req.getLevel())) {
            diagnosticsTelegramService.sendAlert(saved, userId);
        }
    }
}
