package lk.tutionlms.backend.assessment;

import lk.tutionlms.backend.assessment.dto.ResultDto;
import lk.tutionlms.backend.identity.User;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/results")
@PreAuthorize("hasRole('TEACHER')")
public class ResultController {
    private final AssessmentService service;

    public ResultController(AssessmentService service) { this.service = service; }

    @GetMapping
    public List<ResultDto.Summary> list(@AuthenticationPrincipal User user) {
        return service.getTeacherResults(user);
    }

    @GetMapping("/{id}")
    public ResultDto.Detail detail(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.getTeacherResultDetail(id, user);
    }

    @PostMapping("/{id}/publish")
    public ResultDto.Detail publish(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.publishResults(id, user);
    }

    @PostMapping("/{id}/unpublish")
    public ResultDto.Detail unpublish(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.unpublishResults(id, user);
    }
}
