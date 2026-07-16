package uz.tabriko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.tabriko.domain.entity.CrashReport;

public interface CrashReportRepository extends JpaRepository<CrashReport, Long> {
}
