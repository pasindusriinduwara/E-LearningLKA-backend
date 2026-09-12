package lk.tutionlms.backend.assessment;

import lk.tutionlms.backend.assessment.dto.QuizDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfParserService {

    private static final Logger log = LoggerFactory.getLogger(PdfParserService.class);

    // Regex for question starters: "1. ", "1) ", "Q1. ", "Question 1: ", "(1) "
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
            "^(?:(?:Q|Question|QUESTION)\\s*)?(\\d{1,3})[\\.\\)]\\s*(.*)",
            Pattern.CASE_INSENSITIVE
    );

    // Regex for option starters: "A. ", "A) ", "(A) ", "a) ", "1) "
    private static final Pattern OPTION_PATTERN = Pattern.compile(
            "^[(\\[]?([A-Ea-e1-5])[)\\]\\.]\\s*(.*)"
    );

    // Regex for inline answer markers: "Answer: B", "Ans: A", "Correct: (C)"
    private static final Pattern ANSWER_PATTERN = Pattern.compile(
            "(?:Answer|Ans|Correct(?:\\s+Option|\\s+Answer)?)\\s*[:\\-]?\\s*[(\\[]?([A-Ea-e1-5])[)\\]]?",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Extracts text from uploaded PDF using Apache PDFBox and parses questions.
     */
    public List<QuizDto.ParsedQuestionDto> parsePdf(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded PDF file is empty");
        }

        String rawText;
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true); // Layout-aware extraction
            rawText = stripper.getText(document);
        }

        return parseText(rawText);
    }

    /**
     * Parses raw text containing questions into structured question DTOs.
     */
    public List<QuizDto.ParsedQuestionDto> parseText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Collections.emptyList();
        }

        // 1. Separate answer key section if present at the bottom
        Map<Integer, String> bottomAnswerKey = extractBottomAnswerKey(rawText);

        String[] lines = rawText.split("\\r?\\n");
        List<QuizDto.ParsedQuestionDto> parsedQuestions = new ArrayList<>();

        QuizDto.ParsedQuestionDto currentQuestion = null;
        QuizDto.ParsedOptionDto currentOption = null;
        int currentQNum = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Check if this line is part of a bottom answer key section
            if (isAnswerKeyHeader(trimmed)) {
                break; // Stop parsing question content once the answer key table starts
            }

            // Check if line starts with an inline answer
            Matcher answerMatcher = ANSWER_PATTERN.matcher(trimmed);
            if (answerMatcher.find() && currentQuestion != null) {
                String detectedAnswerLetter = answerMatcher.group(1).toUpperCase();
                markCorrectOption(currentQuestion, detectedAnswerLetter);
                continue;
            }

            // Check for new Question start
            Matcher qMatcher = QUESTION_PATTERN.matcher(trimmed);
            if (qMatcher.matches()) {
                // Finalize previous question
                if (currentQuestion != null) {
                    finalizeQuestion(currentQuestion, bottomAnswerKey);
                    parsedQuestions.add(currentQuestion);
                }

                currentQNum = Integer.parseInt(qMatcher.group(1));
                String qText = qMatcher.group(2).trim();

                currentQuestion = QuizDto.ParsedQuestionDto.builder()
                        .questionNumber(currentQNum)
                        .questionText(qText)
                        .marks(BigDecimal.ONE)
                        .options(new ArrayList<>())
                        .build();

                currentOption = null;
                continue;
            }

            // Check for Option start
            Matcher optMatcher = OPTION_PATTERN.matcher(trimmed);
            if (currentQuestion != null && optMatcher.matches()) {
                String label = optMatcher.group(1).toUpperCase();
                String text = optMatcher.group(2).trim();

                currentOption = QuizDto.ParsedOptionDto.builder()
                        .label(normalizeLabel(label))
                        .text(text)
                        .isCorrect(false)
                        .build();

                currentQuestion.getOptions().add(currentOption);
                continue;
            }

            // Multiline continuation
            if (currentOption != null) {
                // Continuation of current option text
                currentOption.setText(currentOption.getText() + " " + trimmed);
            } else if (currentQuestion != null) {
                // Continuation of question text
                currentQuestion.setQuestionText(currentQuestion.getQuestionText() + " " + trimmed);
            }
        }

        // Add last question
        if (currentQuestion != null) {
            finalizeQuestion(currentQuestion, bottomAnswerKey);
            parsedQuestions.add(currentQuestion);
        }

        // If no standard questions matched, try fallback paragraph segmentation
        if (parsedQuestions.isEmpty()) {
            return fallbackSegmenter(rawText);
        }

        return parsedQuestions;
    }

    private void finalizeQuestion(QuizDto.ParsedQuestionDto q, Map<Integer, String> bottomAnswerKey) {
        // If correct answer wasn't inline, check the bottom answer key
        if (!q.isHasCorrectAnswer() && bottomAnswerKey.containsKey(q.getQuestionNumber())) {
            String ans = bottomAnswerKey.get(q.getQuestionNumber());
            markCorrectOption(q, ans);
        }

        // Compute confidence and warnings
        double confidence = 1.0;
        List<String> warnings = new ArrayList<>();

        if (q.getOptions().isEmpty()) {
            confidence -= 0.5;
            warnings.add("No options detected");
        } else if (q.getOptions().size() < 3) {
            confidence -= 0.2;
            warnings.add("Only " + q.getOptions().size() + " options detected");
        }

        if (!q.isHasCorrectAnswer()) {
            confidence -= 0.3;
            warnings.add("No correct answer selected");
        }

        if (q.getQuestionText() == null || q.getQuestionText().length() < 5) {
            confidence -= 0.3;
            warnings.add("Question text is unusually short");
        }

        q.setConfidence(Math.max(0.1, Math.min(1.0, confidence)));
        if (!warnings.isEmpty()) {
            q.setWarning(String.join(", ", warnings));
        }
    }

    private void markCorrectOption(QuizDto.ParsedQuestionDto q, String targetLabel) {
        String normalizedTarget = normalizeLabel(targetLabel);
        for (QuizDto.ParsedOptionDto opt : q.getOptions()) {
            if (opt.getLabel().equalsIgnoreCase(normalizedTarget) ||
                opt.getLabel().equalsIgnoreCase(targetLabel)) {
                opt.setCorrect(true);
                q.setHasCorrectAnswer(true);
                return;
            }
        }
    }

    private String normalizeLabel(String label) {
        return switch (label.toUpperCase()) {
            case "1" -> "A";
            case "2" -> "B";
            case "3" -> "C";
            case "4" -> "D";
            case "5" -> "E";
            default -> label.toUpperCase();
        };
    }

    private boolean isAnswerKeyHeader(String line) {
        String lower = line.toLowerCase();
        return lower.startsWith("answer key") ||
               lower.startsWith("answers:") ||
               lower.startsWith("correct answers") ||
               lower.startsWith("marking scheme");
    }

    private Map<Integer, String> extractBottomAnswerKey(String text) {
        Map<Integer, String> answerKey = new HashMap<>();
        Pattern keyPairPattern = Pattern.compile("(\\d{1,3})\\s*[-.:=)]\\s*([A-Ea-e1-5])");
        Matcher matcher = keyPairPattern.matcher(text);

        while (matcher.find()) {
            try {
                int qNum = Integer.parseInt(matcher.group(1));
                String ans = matcher.group(2).toUpperCase();
                answerKey.put(qNum, ans);
            } catch (NumberFormatException ignored) {}
        }
        return answerKey;
    }

    private List<QuizDto.ParsedQuestionDto> fallbackSegmenter(String rawText) {
        List<QuizDto.ParsedQuestionDto> fallbackList = new ArrayList<>();
        String[] blocks = rawText.split("\\n\\s*\\n");
        int count = 1;

        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.length() < 10) continue;

            QuizDto.ParsedQuestionDto q = QuizDto.ParsedQuestionDto.builder()
                    .questionNumber(count++)
                    .questionText(trimmed)
                    .marks(BigDecimal.ONE)
                    .confidence(0.5)
                    .warning("Review formatting: parsed using fallback segmenter")
                    .options(new ArrayList<>())
                    .build();

            // Default 4 empty placeholder options for teacher convenience
            q.getOptions().add(new QuizDto.ParsedOptionDto("A", "Option A", false));
            q.getOptions().add(new QuizDto.ParsedOptionDto("B", "Option B", false));
            q.getOptions().add(new QuizDto.ParsedOptionDto("C", "Option C", false));
            q.getOptions().add(new QuizDto.ParsedOptionDto("D", "Option D", false));

            fallbackList.add(q);
        }
        return fallbackList;
    }
}
