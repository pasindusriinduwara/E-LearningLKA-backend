package lk.tutionlms.backend.scheduling;

import jakarta.persistence.*;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "class_schedules", indexes = {
        @Index(name = "idx_schedule_batch_day", columnList = "batch_id, day_of_week"),
        @Index(name = "idx_schedule_day_time", columnList = "day_of_week, start_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleItem extends BaseEntity {

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 15)
    private DayOfWeek dayOfWeek;

    @Column(name = "effective_date")
    private LocalDate effectiveDate; // The date when this schedule starts

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "subject")
    private String subject;

    @Column(name = "teacher")
    private String teacher;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "time_text")
    private String time; // Display string e.g. "04:30 PM - 06:30 PM"

    @Column(name = "location")
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false, length = 20)
    private DeliveryMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", nullable = false, length = 20)
    @Builder.Default
    private RecurrenceType recurrence = RecurrenceType.WEEKLY;

    @Column(name = "accent")
    private String accent;
}
