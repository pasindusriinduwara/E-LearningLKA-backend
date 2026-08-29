package lk.tutionlms.backend.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;

@Entity
@Table(name = "learning_materials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningMaterial extends BaseEntity {

    @Column(nullable = false)
    private String title;

    private String subject; // e.g. "Combined Mathematics", "Chemistry", "Physics"

    @Column(name = "resource_type")
    private String type; // "PDF" or "Video"

    @Column(name = "time_text")
    private String time; // "Added 2 hours ago"

    @Column(name = "size_text")
    private String size; // "2.4 MB" or "48 min"

    @Column(name = "file_url")
    private String fileUrl;
}
