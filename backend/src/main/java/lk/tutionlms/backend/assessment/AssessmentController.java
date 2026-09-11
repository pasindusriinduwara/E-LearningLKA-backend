package lk.tutionlms.backend.assessment;

import lk.tutionlms.backend.assessment.dto.QuizDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assessments")
public class AssessmentController {

    private static final Logger log = LoggerFactory.getLogger(AssessmentController.class);

    private final PdfParserService pdfParserService;
    private final AssessmentService assessmentService;

    public AssessmentController(PdfParserService pdfParserService, AssessmentService assessmentService) {
        this.pdfParserService = pdfParserService;
        this.assessmentService = assessmentService;
    }

    /**
     * Upload and extract MCQ questions from a teacher-provided PDF.
     */
    @PostMapping("/parse-pdf")
    public ResponseEntity<?> parsePdf(@RequestParam("file") MultipartFile file) {
        try {
            log.info("Received PDF parse request: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());
            List<QuizDto.ParsedQuestionDto> questions = pdfParserService.parsePdf(file);
            return ResponseEntity.ok(questions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            log.error("Failed to read or parse PDF", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error extracting text from PDF: " + e.getMessage());
        }
    }

    /**
     * Parse raw text containing MCQs (useful for testing or copy-pasted exams).
     */
    @PostMapping("/parse-text")
    public ResponseEntity<List<QuizDto.ParsedQuestionDto>> parseText(@RequestBody QuizDto.ParseTextRequest request) {
        List<QuizDto.ParsedQuestionDto> questions = pdfParserService.parseText(request.getRawText());
        return ResponseEntity.ok(questions);
    }

    /**
     * Persist confirmed and reviewed quiz questions into PostgreSQL.
     */
    @PostMapping("/quiz")
    public ResponseEntity<?> createQuiz(@RequestBody QuizDto.CreateQuizRequest request) {
        try {
            Assessment created = assessmentService.createQuizWithQuestions(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating quiz", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating assessment: " + e.getMessage());
        }
    }

    /**
     * Get all assessments.
     */
    @GetMapping
    public ResponseEntity<List<QuizDto.AssessmentSummaryResponse>> getAllAssessments() {
        return ResponseEntity.ok(assessmentService.getAllAssessments());
    }

    /**
     * Get visible assessments for students (non-hidden, non-deleted).
     */
    @GetMapping("/student")
    public ResponseEntity<List<QuizDto.AssessmentSummaryResponse>> getStudentAssessments() {
        return ResponseEntity.ok(assessmentService.getStudentAssessments());
    }

    /**
     * Get all assessments for a batch.
     */
    @GetMapping("/batch/{batchId}")
    public ResponseEntity<List<QuizDto.AssessmentSummaryResponse>> getAssessmentsByBatch(@PathVariable UUID batchId) {
        return ResponseEntity.ok(assessmentService.getAssessmentsByBatch(batchId));
    }

    /**
     * Get single assessment with questions and options.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getAssessmentById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(assessmentService.getAssessmentById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Toggle hide/unhide visibility of an assessment for students.
     */
    @PatchMapping("/{id}/toggle-hide")
    public ResponseEntity<?> toggleHide(@PathVariable UUID id) {
        try {
            Assessment updated = assessmentService.toggleHideAssessment(id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update an assessment (title, due date, marks, duration).
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateAssessment(
            @PathVariable UUID id,
            @RequestBody QuizDto.UpdateAssessmentRequest request) {
        try {
            Assessment updated = assessmentService.updateAssessment(id, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Soft delete an assessment.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAssessment(@PathVariable UUID id) {
        try {
            assessmentService.deleteAssessment(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
