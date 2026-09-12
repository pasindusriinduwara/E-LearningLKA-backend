package lk.tutionlms.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateStudentProfileRequest {
    @NotBlank(message = "Name is required")
    private String name;
    private String initials;
    private String phoneNumber;
    private String exam;
    private String stream;
    private String medium;
    private LocalDate dateOfBirth;
}
