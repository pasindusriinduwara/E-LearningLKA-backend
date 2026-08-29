package lk.tutionlms.backend.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;

@Entity
@Table(name = "subjects")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subject extends BaseEntity {
    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
