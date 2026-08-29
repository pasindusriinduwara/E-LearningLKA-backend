package lk.tutionlms.backend.sheduling;

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

    // GET http://localhost:8080/api/v1/schedules
    @GetMapping
    public ResponseEntity<List<ScheduleItem>> getAllSchedules() {
        return ResponseEntity.ok(scheduleRepository.findByDeletedFalse());
    }

    // GET http://localhost:8080/api/v1/schedules/upcoming
    @GetMapping("/upcoming")
    public ResponseEntity<List<ScheduleItem>> getUpcomingSchedules() {
        return ResponseEntity.ok(scheduleRepository.findByDeletedFalse());
    }

    // GET http://localhost:8080/api/v1/schedules/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ScheduleItem> getScheduleById(@PathVariable UUID id) {
        return scheduleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST http://localhost:8080/api/v1/schedules
    @PostMapping
    public ResponseEntity<ScheduleItem> createSchedule(@RequestBody ScheduleItem scheduleItem) {
        return ResponseEntity.ok(scheduleRepository.save(scheduleItem));
    }
}
