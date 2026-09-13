package lk.tutionlms.backend.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StudentProfileResponse {
    private UUID id;
    private UUID userId;
    private String studentId;
    private String name;
    private String initials;
    private String email;
    private String phoneNumber;
    private String exam;
    private String stream;
    private String medium;
    private LocalDate dateOfBirth;
}
