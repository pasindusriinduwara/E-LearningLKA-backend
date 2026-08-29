package lk.tutionlms.backend.identity.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {
    private String email;
    private String password;
    private String phoneNumber;
    private String userType;

    private String firstName;
    private String lastName;
    private String dob;
    private String nic;

    private String grade;
    private String stream;
    private String medium;

    private List<String> subjects;
    private String qualification;
    private String experience;
    private String institute;
}