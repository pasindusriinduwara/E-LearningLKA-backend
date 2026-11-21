package lk.tutionlms.backend.assessment;

import lk.tutionlms.backend.assessment.dto.QuizDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfParserService {

    // Regex for question starters: "1. ", "1) ", "Q1. ", "Question 1: ", "(1) "
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
            "^(?:(?:Q|Question|QUESTION)\\s*)?(\\d{1,3})[\\.\\)]\\s*(.*)",
            Pattern.CASE_INSENSITIVE
    );

    // Options can appear more than once on a line in a two-column paper.
    private static final Pattern OPTION_PATTERN = Pattern.compile(
            "(?<!\\S)([(\\[]?)([A-Ea-e1-5])([)\\]\\.])(?=\\s|$)\\s*"
    );

    // Regex for inline answer markers: "Answer: B", "Ans: A", "Correct: (C)"
    private static final Pattern ANSWER_PATTERN = Pattern.compile(
            "^(?:Answer|Ans|Correct(?:\\s+Option|\\s+Answer)?)\\s*[:\\-]?\\s*[(\\[]?([A-Ea-e1-5])[)\\]]?\\s*\\.?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern KEY_PAIR_PATTERN = Pattern.compile(
            "(?:^|[\\s,;|]+)(\\d{1,3})\\s*[-.:=)]\\s*[(\\[]?([A-Ea-e1-5])[)\\]]?(?=$|[\\s,;|])");

    /**
     * Extracts text from uploaded PDF using Apache PDFBox and parses questions.
     */
    public List<QuizDto.ParsedQuestionDto> parsePdf(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded PDF file is empty");
        }

        String rawText;
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new ColumnPreservingTextStripper();
            stripper.setSortByPosition(true); // Layout-aware extraction
            stripper.setShouldSeparateByBeads(false);
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
        rawText = rawText.replace('\u00a0', ' ').replace('\u202f', ' ');

        // 1. Separate answer key section if present at the bottom
        Map<Integer, String> bottomAnswerKey = extractBottomAnswerKey(rawText);

        String[] lines = rawText.split("\\r?\\n");
        List<QuizDto.ParsedQuestionDto> parsedQuestions = new ArrayList<>();

        QuizDto.ParsedQuestionDto currentQuestion = null;
        QuizDto.ParsedOptionDto currentOption = null;
        int numericOptionsRemaining = 0;

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Check if this line is part of a bottom answer key section
            if (isAnswerKeyHeader(trimmed)) {
                break; // Stop parsing question content once the answer key table starts
            }
            Map<Integer, String> keyEntries = answerKeyEntries(trimmed);
            if (currentQuestion != null && !currentQuestion.getOptions().isEmpty()
                    && (keyEntries.size() > 1 || (!keyEntries.isEmpty() && isUnambiguousKeyRow(trimmed)))) break;

            // Check if line starts with an inline answer
            Matcher answerMatcher = ANSWER_PATTERN.matcher(trimmed);
            if (answerMatcher.find() && currentQuestion != null) {
                String detectedAnswerLetter = answerMatcher.group(1).toUpperCase();
                markCorrectOption(currentQuestion, detectedAnswerLetter);
                continue;
            }

            // Check for new Question start
            Matcher qMatcher = QUESTION_PATTERN.matcher(trimmed);
            boolean numericOption = false;
            if (currentQuestion != null) {
                if (numericOptionsRemaining == 0 && currentQuestion.getOptions().isEmpty()) {
                    numericOptionsRemaining = numericOptionGroupLength(lines, lineIndex);
                }
                numericOption = numericOptionsRemaining > 0;
            }
            if (qMatcher.matches() && !numericOption) {
                // Finalize previous question
                if (currentQuestion != null) {
                    finalizeQuestion(currentQuestion, bottomAnswerKey);
                    parsedQuestions.add(currentQuestion);
                }

                int currentQNum = Integer.parseInt(qMatcher.group(1));
                String qText = qMatcher.group(2).trim();

                currentQuestion = QuizDto.ParsedQuestionDto.builder()
                        .questionNumber(currentQNum)
                        .questionText(qText)
                        .marks(BigDecimal.ONE)
                        .options(new ArrayList<>())
                        .build();

                currentOption = null;
                numericOptionsRemaining = 0;
                continue;
            }

            // Check for Option start
            List<QuizDto.ParsedOptionDto> options = parseOptionLine(trimmed);
            if (currentQuestion != null && !options.isEmpty()) {
                currentQuestion.getOptions().addAll(options);
                currentOption = options.get(options.size() - 1);
                if (numericOption) numericOptionsRemaining--;
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
        q.getOptions().sort(Comparator.comparing(QuizDto.ParsedOptionDto::getLabel));
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
        boolean inKeySection = false;
        for (String line : text.split("\\r?\\n")) {
            String content = line.trim();
            if (isAnswerKeyHeader(content)) {
                inKeySection = true;
                content = content.replaceFirst("(?i)^(?:answer key|answers:|correct answers|marking scheme)\\s*:?\\s*", "");
            }
            Map<Integer, String> entries = answerKeyEntries(content);
            // A lone "1. A" can be a numeric option rather than an answer key.
            if (inKeySection || entries.size() > 1 || isUnambiguousKeyRow(content)) answerKey.putAll(entries);
        }
        return answerKey;
    }

    private boolean isUnambiguousKeyRow(String line) { return line.matches("^\\d{1,3}\\s*[-:=].*"); }

    private Map<Integer, String> answerKeyEntries(String line) {
        if (line.isEmpty() || !KEY_PAIR_PATTERN.matcher(line).replaceAll("").matches("[\\s,;|]*")) return Map.of();
        Map<Integer, String> entries = new LinkedHashMap<>();
        Matcher matcher = KEY_PAIR_PATTERN.matcher(line);
        while (matcher.find()) entries.put(Integer.parseInt(matcher.group(1)), normalizeLabel(matcher.group(2)));
        return entries;
    }

    private record OptionMarker(int start, int textStart, String label, boolean bracketed) {}

    private List<QuizDto.ParsedOptionDto> parseOptionLine(String line) {
        Matcher matcher = OPTION_PATTERN.matcher(line);
        List<OptionMarker> markers = new ArrayList<>();
        while (matcher.find()) {
            markers.add(new OptionMarker(matcher.start(), matcher.end(), normalizeLabel(matcher.group(2)),
                    !matcher.group(1).isEmpty() || !".".equals(matcher.group(3))));
        }
        if (markers.isEmpty() || markers.get(0).start() != 0) return List.of();
        boolean sequentialRow = markers.size() >= 3;
        for (int i = 1; i < markers.size(); i++) {
            sequentialRow &= markers.get(i).label().charAt(0) == markers.get(i - 1).label().charAt(0) + 1;
        }
        List<OptionMarker> boundaries = new ArrayList<>();
        boundaries.add(markers.get(0));
        for (int i = 1; i < markers.size(); i++) {
            OptionMarker marker = markers.get(i);
            int spaceStart = marker.start();
            while (spaceStart > 0 && Character.isWhitespace(line.charAt(spaceStart - 1))) spaceStart--;
            String separator = line.substring(spaceStart, marker.start());
            if (sequentialRow || marker.bracketed() || separator.length() >= 2 || separator.contains("\t")) boundaries.add(marker);
        }
        List<QuizDto.ParsedOptionDto> options = new ArrayList<>();
        for (int i = 0; i < boundaries.size(); i++) {
            OptionMarker marker = boundaries.get(i);
            int end = i + 1 < boundaries.size() ? boundaries.get(i + 1).start() : line.length();
            options.add(new QuizDto.ParsedOptionDto(marker.label(), line.substring(marker.textStart(), end).trim(), false));
        }
        return options;
    }

    private int numericOptionGroupLength(String[] lines, int start) {
        int expected = 1;
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (!line.matches("^[(\\[]?" + expected + "[)\\]\\.]\\s+.+")) break;
            expected++;
            if (expected > 5) break;
        }
        return expected >= 3 ? expected - 1 : 0;
    }

    /** Preserve the wide horizontal gap between columns instead of flattening it to one space. */
    private static final class ColumnPreservingTextStripper extends PDFTextStripper {
        private TextPosition previous;
        private boolean separatorPending;

        ColumnPreservingTextStripper() throws IOException { super(); }

        @Override protected void writeWordSeparator() { separatorPending = true; }

        @Override protected void writeLineSeparator() throws IOException {
            previous = null; separatorPending = false;
            super.writeLineSeparator();
        }

        @Override protected void startPage(org.apache.pdfbox.pdmodel.PDPage page) throws IOException {
            previous = null; separatorPending = false;
            super.startPage(page);
        }

        @Override protected void writeString(String text, List<TextPosition> positions) throws IOException {
            if (separatorPending) {
                boolean columnGap = false;
                if (previous != null && !positions.isEmpty()) {
                    TextPosition first = positions.get(0);
                    float gap = first.getXDirAdj() - previous.getXDirAdj() - previous.getWidthDirAdj();
                    float threshold = Math.max(Math.max(first.getWidthOfSpace(), previous.getWidthOfSpace()) * 3,
                            first.getFontSizeInPt() * 1.5f);
                    columnGap = Math.abs(first.getYDirAdj() - previous.getYDirAdj()) < first.getHeightDir() && gap > threshold;
                }
                if (columnGap) super.writeString("\t");
                else super.writeWordSeparator();
                separatorPending = false;
            }
            super.writeString(text, positions);
            if (!positions.isEmpty()) previous = positions.get(positions.size() - 1);
        }
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
