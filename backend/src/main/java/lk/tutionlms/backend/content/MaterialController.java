package lk.tutionlms.backend.content;

import lombok.RequiredArgsConstructor;
import lk.tutionlms.backend.identity.User;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialRepository materialRepository;

    @GetMapping("/recent")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<LearningMaterial>> getRecentMaterials(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(materialRepository.findByStudentUserId(user.getId()));
    }
}
