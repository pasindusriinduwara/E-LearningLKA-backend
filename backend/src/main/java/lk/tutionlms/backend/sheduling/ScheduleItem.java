package lk.tutionlms.backend.sheduling;

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

/**
 * ScheduleItem maps to the 'class_schedules' table in PostgreSQL.
 * Matches the TypeScript ScheduleItem interface on the frontend.
 */
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

    // Day label displayed on UI (e.g., "Today", "Tomorrow", "Wed")
    @Column(name = "day_of_week")
    private String day;

    // Date tag (e.g., "25 AUG", "26 AUG")
    @Column(name = "date_text")
    private String date;

    // Lesson topic or class title (e.g., "Pure Mathematics")
    @Column(name = "title")
    private String title;

    // Subject name (e.g., "Combined Mathematics", "Chemistry", "Physics")
    @Column(name = "subject")
    private String subject;

    // Teacher name (e.g., "Mr. K. Perera")
    @Column(name = "teacher")
    private String teacher;

    // Time slot text (e.g., "4:30 PM - 6:30 PM")
    @Column(name = "time_text")
    private String time;

    // Studio/classroom or live room (e.g., "Nugegoda Studio 2", "Live classroom")
    @Column(name = "location")
    private String location;

    // Delivery mode: "In person" or "Online"
    @Column(name = "delivery_mode")
    private String mode;

    // UI Badge accent color: "coral", "green", "yellow"
    @Column(name = "accent")
    private String accent;
}
