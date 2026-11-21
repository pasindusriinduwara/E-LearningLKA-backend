package lk.tutionlms.backend.assessment;

import lk.tutionlms.backend.assessment.dto.QuizDto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfParserServiceTests {
    private final PdfParserService parser = new PdfParserService();

    @Test void parsesBothColumnsFromAnActualPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(); document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                line(content, 40, 740, "Each correct answer earns 1 mark. There is no negative marking.");
                line(content, 40, 710, "1. Which planet is known as the Red Planet?");
                // Draw the left column first, as many PDF writers do.
                line(content, 60, 690, "A. Venus"); line(content, 60, 670, "C. Mars");
                line(content, 320, 690, "B. Jupiter"); line(content, 320, 670, "D. Saturn");
                line(content, 40, 640, "2. How many sides does a hexagon have?");
                line(content, 60, 620, "A. Five"); line(content, 320, 620, "B. Six");
                line(content, 60, 600, "C. Seven"); line(content, 320, 600, "D. Eight");
            }
            document.save(bytes);
            var result = parser.parsePdf(new MockMultipartFile("file", "two-columns.pdf", "application/pdf", bytes.toByteArray()));
            assertEquals(2, result.size());
            assertOptions(result.get(0), "Venus", "Jupiter", "Mars", "Saturn");
            assertOptions(result.get(1), "Five", "Six", "Seven", "Eight");
            assertFalse(result.get(0).isHasCorrectAnswer());
        }
    }

    @Test void parsesTwoColumnTextAndBottomAnswerKey() {
        var questions = parser.parseText("1. Which planet is red?\nA. Venus\tB. Jupiter\nC. Mars    D. Saturn\nAnswer Key: 1. C");
        assertEquals(1, questions.size());
        assertOptions(questions.get(0), "Venus", "Jupiter", "Mars", "Saturn");
        assertTrue(questions.get(0).getOptions().get(2).isCorrect());
    }

    @Test void preservesSingleColumnMultilineTextAndDoesNotSplitVitaminNames() {
        var q = parser.parseText("1. Choose the best description.\nA. Vitamin B. deficiency\ncan cause symptoms.\nB. A second choice\nC. Another choice\nD. Final choice\nAnswer: B").get(0);
        assertOptions(q, "Vitamin B. deficiency can cause symptoms.", "A second choice", "Another choice", "Final choice");
        assertTrue(q.getOptions().get(1).isCorrect());
    }

    @Test void ordersLabelsAfterColumnWiseTextExtraction() {
        var q = parser.parseText("1. Which planet is red?\nA. Venus\nC. Mars\nB. Jupiter\nD. Saturn\nAnswer: C").get(0);
        assertOptions(q, "Venus", "Jupiter", "Mars", "Saturn");
        assertTrue(q.getOptions().get(2).isCorrect());
    }

    @Test void questionProseNeverBecomesAnAnswerKey() {
        var q = parser.parseText("1. Choose the correct answer to this question.\nA. First\nB. Second\nC. Third\nD. Fourth").get(0);
        assertFalse(q.isHasCorrectAnswer());
        assertTrue(q.getOptions().stream().noneMatch(QuizDto.ParsedOptionDto::isCorrect));
    }

    @Test void supportsCollapsedInlineAndBracketedOptions() {
        var all = parser.parseText("1. Choose a planet.\nA. Venus B. Jupiter C. Mars D. Saturn\n2. Choose another planet.\n(a) Venus (b) Jupiter\n(c) Mars (d) Saturn\nAnswer: c");
        assertEquals(2, all.size());
        all.forEach(q -> assertOptions(q, "Venus", "Jupiter", "Mars", "Saturn"));
        assertTrue(all.get(1).getOptions().get(2).isCorrect());
    }

    @Test void numericOptionGroupsDoNotBecomeQuestions() {
        var all = parser.parseText("1. Which gas is needed?\n1) Nitrogen\n2) Oxygen\n3) Carbon dioxide\n4) Argon\nAnswer: 3\n2. Which planet is red?\nA. Venus\nB. Jupiter\nC. Mars\nD. Saturn");
        assertEquals(2, all.size());
        assertOptions(all.get(0), "Nitrogen", "Oxygen", "Carbon dioxide", "Argon");
        assertTrue(all.get(0).getOptions().get(2).isCorrect());
    }

    @Test void unheadedAnswerKeyIsNotAppendedToTheLastOption() {
        var q = parser.parseText("1. Which planet is red?\nA. Venus\nB. Jupiter\nC. Mars\nD. Saturn\n1-C").get(0);
        assertOptions(q, "Venus", "Jupiter", "Mars", "Saturn");
        assertTrue(q.getOptions().get(2).isCorrect());
    }

    @Test void numericOptionTextIsNotMistakenForAnAnswerKey() {
        var q = parser.parseText("1. Which letter comes first?\n1) B\n2) A\n3) C\n4) D").get(0);
        assertOptions(q, "B", "A", "C", "D");
        assertFalse(q.isHasCorrectAnswer());
    }

    private void assertOptions(QuizDto.ParsedQuestionDto q, String... texts) {
        assertEquals(List.of("A", "B", "C", "D"), q.getOptions().stream().map(QuizDto.ParsedOptionDto::getLabel).toList());
        assertEquals(List.of(texts), q.getOptions().stream().map(QuizDto.ParsedOptionDto::getText).toList());
    }

    private void line(PDPageContentStream content, float x, float y, String text) throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
        content.newLineAtOffset(x, y); content.showText(text); content.endText();
    }
}
