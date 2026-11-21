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
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lk.tutionlms.backend.identity.User;

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
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> parsePdf(@RequestParam("file") MultipartFile file) {
        try {
            log.info("Received PDF parse request: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());
            List<QuizDto.ParsedQuestionDto> questions = pdfParserService.parsePdf(file);
            return ResponseEntity.ok(questions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Failed to read PDF", e);
        }
    }

    /**
     * Parse raw text containing MCQs (useful for testing or copy-pasted exams).
     */
    @PostMapping("/parse-text")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<QuizDto.ParsedQuestionDto>> parseText(@RequestBody QuizDto.ParseTextRequest request) {
        List<QuizDto.ParsedQuestionDto> questions = pdfParserService.parseText(request.getRawText());
        return ResponseEntity.ok(questions);
    }

    /**
     * Persist confirmed and reviewed quiz questions into PostgreSQL.
     */
    @PostMapping("/quiz")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> createQuiz(
            @RequestBody QuizDto.CreateQuizRequest request,
            @AuthenticationPrincipal User currentUser) {
        try {
            Assessment created = assessmentService.createQuizWithQuestions(request, currentUser);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Get all assessments.
     */
    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<QuizDto.AssessmentSummaryResponse>> getAllAssessments(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(assessmentService.getAllAssessments(currentUser));
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
     * Get assessments by batch (for teacher management or enrolled students).
     */
    @GetMapping("/batch/{batchId}")
    public ResponseEntity<List<QuizDto.AssessmentSummaryResponse>> getAssessmentsByBatch(
            @PathVariable UUID batchId,
            @RequestParam(required = false) String studentId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal lk.tutionlms.backend.identity.User currentUser) {
        if (currentUser != null && ("TEACHER".equalsIgnoreCase(currentUser.getUserType())
                || "ADMIN".equalsIgnoreCase(currentUser.getUserType()))) {
            return ResponseEntity.ok(assessmentService.getAssessmentsByBatch(batchId, currentUser));
        }
        return ResponseEntity.ok(assessmentService.getStudentAssessmentsByBatch(batchId, currentUser, studentId));
    }

    /**
     * Secure, anti-cheating endpoint for students to take an assessment.
     * Strips all correct answer markers and explanations.
     */
    @GetMapping("/{id}/take")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getAssessmentForTaking(
            @PathVariable UUID id,
            @RequestParam(required = false) String studentId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal lk.tutionlms.backend.identity.User currentUser) {
        try {
            return ResponseEntity.ok(assessmentService.getAssessmentForTaking(id, currentUser, studentId));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> startQuizAttempt(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        try {
            return ResponseEntity.ok(assessmentService.startQuizAttempt(id, currentUser));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Server-side grading of student quiz submission.
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> submitQuiz(
            @PathVariable UUID id,
            @RequestBody QuizDto.QuizSubmissionRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal lk.tutionlms.backend.identity.User currentUser) {
        try {
            QuizDto.QuizSubmissionResultResponse result = assessmentService.submitQuiz(id, request, currentUser);
            return ResponseEntity.ok(result);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
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
            QuizDto.QuizSubmissionResultResponse result = assessmentService.getStudentSubmission(id, studentId,
                    currentUser);
            return ResponseEntity.ok(result);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
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
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Teacher evaluation and grading of a student's submission.
     */
    @PostMapping("/{id}/submissions/{submissionId}/grade")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> gradeSubmission(
            @PathVariable UUID id,
            @PathVariable UUID submissionId,
            @RequestBody QuizDto.GradeSubmissionRequest request,
            @AuthenticationPrincipal User currentUser) {
        try {
            QuizDto.AssessmentSubmissionSummary result = assessmentService.gradeSubmission(id, submissionId, request, currentUser);
            return ResponseEntity.ok(result);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Upload question paper or answer sheet.
     */
    @PostMapping("/upload-paper")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> uploadPaperFile(@RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(assessmentService.uploadPaperFile(file, currentUser));
    }

    @PostMapping("/{id}/upload-answer")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, String>> uploadAnswer(@PathVariable UUID id,
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(assessmentService.uploadAnswerFile(id, file, currentUser));
    }

    /**
     * Serve locally stored assessment files.
     */
    @GetMapping("/files/{filename}")
    public ResponseEntity<org.springframework.core.io.Resource> serveFile(@PathVariable String filename,
            @AuthenticationPrincipal User currentUser) {
        assessmentService.verifyFileAccess(filename, currentUser);
        try {
            java.nio.file.Path baseDir = java.nio.file.Paths.get("uploads", "assessments").toAbsolutePath().normalize();
            java.nio.file.Path filePath = baseDir.resolve(filename).normalize();
            if (!filePath.startsWith(baseDir)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(
                    filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                String contentType = "application/octet-stream";
                if (filename.endsWith(".pdf"))
                    contentType = "application/pdf";
                else if (filename.endsWith(".png"))
                    contentType = "image/png";
                else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg"))
                    contentType = "image/jpeg";
                return ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, contentType)
                        .header("X-Content-Type-Options", "nosniff")
                        .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, no-store")
                        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
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
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> getAssessmentSubmissions(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        try {
            return ResponseEntity.ok(assessmentService.getAssessmentSubmissions(id, currentUser));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Get single assessment with questions and options.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> getAssessmentById(@PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        try {
            return ResponseEntity.ok(assessmentService.getTeacherAssessment(id, currentUser));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Toggle hide/unhide visibility of an assessment for students.
     */
    @PatchMapping("/{id}/toggle-hide")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> toggleHide(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        try {
            Assessment updated = assessmentService.toggleHideAssessment(id, currentUser);
            return ResponseEntity.ok(updated);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update an assessment (title, due date, marks, duration).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> updateAssessment(
            @PathVariable UUID id,
            @RequestBody QuizDto.UpdateAssessmentRequest request,
            @AuthenticationPrincipal User currentUser) {
        try {
            Assessment updated = assessmentService.updateAssessment(id, request, currentUser);
            return ResponseEntity.ok(updated);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Soft delete an assessment.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> deleteAssessment(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        try {
            assessmentService.deleteAssessment(id, currentUser);
            return ResponseEntity.noContent().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
