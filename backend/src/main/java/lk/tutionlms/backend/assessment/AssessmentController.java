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
     public ResponseEntity<List<QuizDto.AssessmentSummaryResponse>> getStudentAssessments(
             @RequestParam(required = false) String studentId,
             @org.springframework.security.core.annotation.AuthenticationPrincipal lk.tutionlms.backend.identity.User currentUser) {
         return ResponseEntity.ok(assessmentService.getStudentAssessments(currentUser, studentId));
     }

     /**
      * Secure, anti-cheating endpoint for students to take an assessment.
      * Strips all correct answer markers and explanations.
      */
     @GetMapping("/{id}/take")
     public ResponseEntity<?> getAssessmentForTaking(@PathVariable UUID id) {
         try {
             return ResponseEntity.ok(assessmentService.getAssessmentForTaking(id));
         } catch (IllegalArgumentException e) {
             return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
         }
     }

     /**
      * Server-side grading of student quiz submission.
      */
     @PostMapping("/{id}/submit")
     public ResponseEntity<?> submitQuiz(
             @PathVariable UUID id,
             @RequestBody QuizDto.QuizSubmissionRequest request,
             @org.springframework.security.core.annotation.AuthenticationPrincipal lk.tutionlms.backend.identity.User currentUser) {
         try {
             QuizDto.QuizSubmissionResultResponse result = assessmentService.submitQuiz(id, request, currentUser);
             return ResponseEntity.ok(result);
         } catch (IllegalArgumentException e) {
             return ResponseEntity.badRequest().body(e.getMessage());
         }
     }

     /**
      * Get a student's submission result and answer breakdown.
      */
     @GetMapping("/{id}/my-submission")
     public ResponseEntity<?> getStudentSubmission(
             @PathVariable UUID id,
             @RequestParam(required = false) String studentId,
             @org.springframework.security.core.annotation.AuthenticationPrincipal lk.tutionlms.backend.identity.User currentUser) {
         try {
             QuizDto.QuizSubmissionResultResponse result = assessmentService.getStudentSubmission(id, studentId, currentUser);
             return ResponseEntity.ok(result);
         } catch (IllegalArgumentException e) {
             return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
         }
     }

    /**
     * Submit an essay-type or paper assignment.
     */
    @PostMapping("/{id}/submit-essay")
    public ResponseEntity<?> submitEssay(
            @PathVariable UUID id,
            @RequestBody QuizDto.EssaySubmissionRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal lk.tutionlms.backend.identity.User currentUser) {
        try {
            QuizDto.QuizSubmissionResultResponse result = assessmentService.submitEssay(id, request, currentUser);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Teacher evaluation and grading of a student's submission.
     */
    @PostMapping("/{id}/submissions/{submissionId}/grade")
    public ResponseEntity<?> gradeSubmission(
            @PathVariable UUID id,
            @PathVariable UUID submissionId,
            @RequestBody QuizDto.GradeSubmissionRequest request) {
        try {
            QuizDto.AssessmentSubmissionSummary result = assessmentService.gradeSubmission(id, submissionId, request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Upload question paper or answer sheet.
     */
    @PostMapping("/upload-paper")
    public ResponseEntity<?> uploadPaperFile(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(assessmentService.uploadPaperFile(file));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /**
     * Serve locally stored assessment files.
     */
    @GetMapping("/files/{filename}")
    public ResponseEntity<org.springframework.core.io.Resource> serveFile(@PathVariable String filename) {
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get("uploads", "assessments").resolve(filename).normalize();
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                String contentType = "application/octet-stream";
                if (filename.endsWith(".pdf")) contentType = "application/pdf";
                else if (filename.endsWith(".png")) contentType = "image/png";
                else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) contentType = "image/jpeg";
                return ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, contentType)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all student submissions for an assessment (Teacher view).
     */
    @GetMapping("/{id}/submissions")
    public ResponseEntity<?> getAssessmentSubmissions(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(assessmentService.getAssessmentSubmissions(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
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
