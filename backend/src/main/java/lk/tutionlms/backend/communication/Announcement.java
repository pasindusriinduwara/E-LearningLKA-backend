package lk.tutionlms.backend.communication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Announcement extends BaseEntity {

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "announcement_type")
    private String type; 

    @Column(name = "time_text")
    private String time; 
}
