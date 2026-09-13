package lk.tutionlms.backend.content;

import lombok.RequiredArgsConstructor;
import lk.tutionlms.backend.identity.User;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping("/recent")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<MaterialResponse>> getRecentMaterials(
            Authentication authentication,
            @AuthenticationPrincipal User user) {
        String email = authentication != null ? authentication.getName() : (user != null ? user.getEmail() : null);
        return ResponseEntity.ok(materialService.getStudentRecentMaterials(email));
    }

    @GetMapping("/batch/{batchId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<MaterialResponse>> getBatchMaterials(
            Authentication authentication,
            @AuthenticationPrincipal User user,
            @PathVariable UUID batchId) {
        String email = authentication != null ? authentication.getName() : (user != null ? user.getEmail() : null);
        return ResponseEntity.ok(materialService.getStudentBatchMaterials(email, batchId));
    }
}
