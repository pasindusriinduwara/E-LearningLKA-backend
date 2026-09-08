package lk.tutionlms.backend.scheduling;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleRepository scheduleRepository;

    @GetMapping
    public ResponseEntity<List<ScheduleItem>> getAllSchedules() {
        return ResponseEntity.ok(scheduleRepository.findByDeletedFalse());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<ScheduleItem>> getUpcomingSchedules() {
        return ResponseEntity.ok(scheduleRepository.findByDeletedFalse());
    }

    @GetMapping("/batch/{batchId}")
    public ResponseEntity<List<ScheduleItem>> getBatchSchedules(@PathVariable UUID batchId) {
        return ResponseEntity.ok(scheduleRepository.findByBatchIdAndDeletedFalse(batchId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduleItem> getScheduleById(@PathVariable UUID id) {
        return scheduleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ScheduleItem> createSchedule(@RequestBody ScheduleItem scheduleItem) {
        return ResponseEntity.ok(scheduleRepository.save(scheduleItem));
    }
}
