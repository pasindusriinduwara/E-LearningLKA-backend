package lk.tutionlms.backend.identity.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "Email address is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
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