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
    public ResponseEntity<Batch> createBatch(@RequestBody CreateBatchDto dto) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String teacherEmail = authentication.getName();

        return ResponseEntity.ok(
                batchService.createBatch(teacherEmail, dto)
        );
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