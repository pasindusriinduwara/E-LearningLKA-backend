package lk.tutionlms.backend.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;

@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String initials;

    @Column(name = "student_id", unique = true, nullable = false)
    private String studentId;

    private String exam;

    private String stream;

    private String medium;
}
