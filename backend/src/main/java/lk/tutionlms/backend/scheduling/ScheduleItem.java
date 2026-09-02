package lk.tutionlms.backend.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "class_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleItem extends BaseEntity {

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "day_of_week")
    private String day;

    @Column(name = "date_text")
    private String date;

    @Column(name = "title")
    private String title;

    @Column(name = "subject")
    private String subject;

    @Column(name = "teacher")
    private String teacher;

    @Column(name = "start_time")
    private java.time.LocalTime startTime;

    @Column(name = "end_time")
    private java.time.LocalTime endTime;

    @Column(name = "time_text")
    private String time;

    @Column(name = "location")
    private String location;

    @Column(name = "delivery_mode")
    private String mode;

    @Column(name = "accent")
    private String accent;
}
