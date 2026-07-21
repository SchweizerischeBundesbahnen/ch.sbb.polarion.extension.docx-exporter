package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.Paragraph;
import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.fmt;
import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.paragraphs;
import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.plain;
import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.seg;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Focused per-feature counterparts to the cumulative {@link FormattingConversionTest}: each test exercises a
 * single inline formatting attribute in isolation and asserts the exact segment sequence pandoc-service
 * produces. The expected model is always the simulated object model (no reference DOCX) — the smallest
 * possible failure surface, so a regression points straight at the affected attribute.
 */
@SkipTestWhenParamNotSet
class FormattingCasesTest extends BasePandocTest {

    @Test
    void bold() throws Exception {
        assertSingleParagraph("bold", "<p>plain <span style=\"font-weight: bold;\">styled</span> text</p>",
                plain("plain "), seg(fmt().bold(), "styled"), plain(" text"));
    }

    @Test
    void italic() throws Exception {
        assertSingleParagraph("italic", "<p>plain <span style=\"font-style: italic;\">styled</span> text</p>",
                plain("plain "), seg(fmt().italic(), "styled"), plain(" text"));
    }

    @Test
    void underline() throws Exception {
        assertSingleParagraph("underline", "<p>plain <span style=\"text-decoration: underline;\">styled</span> text</p>",
                plain("plain "), seg(fmt().underline(), "styled"), plain(" text"));
    }

    @Test
    void strikeThrough() throws Exception {
        assertSingleParagraph("strikeThrough", "<p>plain <span style=\"text-decoration: line-through;\">styled</span> text</p>",
                plain("plain "), seg(fmt().strike(), "styled"), plain(" text"));
    }

    @Test
    void superscript() throws Exception {
        assertSingleParagraph("superscript", "<p>plain <sup>styled</sup> text</p>",
                plain("plain "), seg(fmt().superscript(), "styled"), plain(" text"));
    }

    @Test
    void subscript() throws Exception {
        assertSingleParagraph("subscript", "<p>plain <sub>styled</sub> text</p>",
                plain("plain "), seg(fmt().subscript(), "styled"), plain(" text"));
    }

    @Test
    void textColor() throws Exception {
        assertSingleParagraph("textColor", "<p>plain <span style=\"color: #FF0000;\">styled</span> text</p>",
                plain("plain "), seg(fmt().color("FF0000"), "styled"), plain(" text"));
    }

    @Test
    void highlight() throws Exception {
        assertSingleParagraph("highlight", "<p>plain <span style=\"background-color: #FFFF33;\">styled</span> text</p>",
                plain("plain "), seg(fmt().highlight("FFFF33"), "styled"), plain(" text"));
    }

    @Test
    void fontSize() throws Exception {
        assertSingleParagraph("fontSize", "<p>plain <span style=\"font-size: 16pt;\">styled</span> text</p>",
                plain("plain "), seg(fmt().size(32), "styled"), plain(" text"));
    }

    private void assertSingleParagraph(String reportName, String bodyHtml, Segment... expectedSegments) throws Exception {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/></head><body>" + bodyHtml + "</body></html>";
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().build());
        assertNotNull(docBytes);
        writeReportDocx("formattingCase_" + reportName + "_generated", docBytes);

        assertEquals(List.of(new Paragraph("BodyText", List.of(expectedSegments), List.of())), paragraphs(docBytes));
    }
}
