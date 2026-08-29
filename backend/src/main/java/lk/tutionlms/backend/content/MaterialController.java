package lk.tutionlms.backend.content;

import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<List<LearningMaterial>> getRecentMaterials() {
        return ResponseEntity.ok(materialRepository.findByDeletedFalse());
    }
}
