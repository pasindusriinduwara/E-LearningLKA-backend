package lk.tutionlms.backend.identity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "teachers")
public class Teacher {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", unique = true)
    private UUID userId;

    @Builder.Default
    private String title = "Mr.";

    @Column(nullable = false)
    private String name;

    private String qualification;

    @Column(columnDefinition = "TEXT")
    private String bio;
}