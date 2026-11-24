package lk.tutionlms.backend.batch;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @PostMapping
    public ResponseEntity<Batch> createBatch(@jakarta.validation.Valid @RequestBody CreateBatchDto dto) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String teacherEmail = authentication.getName();

        return ResponseEntity.ok(
                batchService.createBatch(teacherEmail, dto)
        );
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Batch> update(@PathVariable java.util.UUID id,
            @jakarta.validation.Valid @RequestBody CreateBatchDto request, Authentication authentication) {
        return ResponseEntity.ok(batchService.updateBatch(authentication.getName(), id, request));
    }

    public record StatusRequest(@jakarta.validation.constraints.NotNull Boolean active) {}

    @PatchMapping("/{id}/status")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Batch> status(@PathVariable java.util.UUID id,
            @jakarta.validation.Valid @RequestBody StatusRequest request, Authentication authentication) {
        return ResponseEntity.ok(batchService.setActive(authentication.getName(), id, request.active()));
    }

    @GetMapping
    public ResponseEntity<List<AvailableBatchResponse>> getAvailableBatches(Authentication authentication) {
        String userEmail = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(
                batchService.getAvailableBatches(userEmail)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<AvailableBatchResponse> getBatchById(
            @PathVariable java.util.UUID id,
            Authentication authentication
    ) {
        String userEmail = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(
                batchService.getBatchById(id, userEmail)
        );
    }
}
