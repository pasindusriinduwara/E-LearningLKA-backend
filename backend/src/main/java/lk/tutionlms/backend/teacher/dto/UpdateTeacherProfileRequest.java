package lk.tutionlms.backend.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTeacherProfileRequest {
    private String title;
    @NotBlank(message = "Name is required")
    private String name;
    private String initials;
    private String phoneNumber;
    private String qualification;
    private String bio;
}
