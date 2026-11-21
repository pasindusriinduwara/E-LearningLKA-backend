package lk.tutionlms.backend.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Ownership for locally stored files, including uploads not yet attached to a paper. */
@Entity
@Table(name = "assessment_files")
@Getter
@Setter
public class AssessmentFile extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String filename;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "answer_sheet", nullable = false)
    private boolean answerSheet;
}
