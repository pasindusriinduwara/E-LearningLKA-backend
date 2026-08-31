package lk.tutionlms.backend.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "learning_materials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningMaterial extends BaseEntity {

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(nullable = false)
    private String title;

    private String subject; 

    @Column(name = "resource_type", nullable = false, length = 50)
    private String type; 

    @Column(name = "time_text")
    private String time; 

    @Column(name = "size_text")
    private String size; 

    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @Column(name = "cloudinary_public_id", length = 255)
    private String cloudinaryPublicId;
}
