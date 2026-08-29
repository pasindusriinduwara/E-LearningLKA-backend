package lk.tutionlms.backend.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String password;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "user_type", nullable = false, length = 30)
    private String userType; // "STUDENT", "TEACHER", "ADMIN"

    @Column(length = 20)
    @Builder.Default
    private String status = "ACTIVE";
}
