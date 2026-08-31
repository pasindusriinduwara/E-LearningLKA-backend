package lk.tutionlms.backend.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {
    private String id;
    private String userId;
    private String email;
    private String role;
    private String name;
    private String initials;
    private String title;
    private String studentId;
    private String exam;
    private String stream;
    private String medium;
    private String qualification;
    private String bio;
}
