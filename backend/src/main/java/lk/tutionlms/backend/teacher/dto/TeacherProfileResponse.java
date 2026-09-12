package lk.tutionlms.backend.teacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeacherProfileResponse {
    private UUID id;
    private UUID userId;
    private String title;
    private String name;
    private String initials;
    private String email;
    private String phoneNumber;
    private String qualification;
    private String bio;
}
