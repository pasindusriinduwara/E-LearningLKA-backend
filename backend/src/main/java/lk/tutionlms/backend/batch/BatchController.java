package lk.tutionlms.backend.batch;

import java.util.List;

import lk.tutionlms.backend.academic.Batch;
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
    public ResponseEntity<List<AvailableBatchResponse>> getAvailableBatches() {
        return ResponseEntity.ok(
                batchService.getAvailableBatches()
        );
    }
}