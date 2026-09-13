package lk.tutionlms.backend.batch;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AvailableBatchResponse(
        UUID id,
        String name,
        String subject,
        String teacher,
        String teacherTitle,
        String teacherQualification,
        String teacherBio,
        UUID teacherId,
        String schedule,
        List<String> scheduleList,
        String status,
        String examYear,
        BigDecimal monthlyFee,
        String deliveryMode,
        Long enrolledCount,
        Long materialsCount,
        List<ScheduleSummaryDto> schedules,
        UUID requestId
) {
    public AvailableBatchResponse(
            UUID id,
            String name,
            String subject,
            String teacher,
            String teacherTitle,
            String teacherQualification,
            String teacherBio,
            UUID teacherId,
            String schedule,
            List<String> scheduleList,
            String status,
            String examYear,
            BigDecimal monthlyFee,
            String deliveryMode
    ) {
        this(
                id,
                name,
                subject,
                teacher,
                teacherTitle,
                teacherQualification,
                teacherBio,
                teacherId,
                schedule,
                scheduleList,
                status,
                examYear,
                monthlyFee,
                deliveryMode,
                0L,
                0L,
                List.of(),
                null
        );
    }
}