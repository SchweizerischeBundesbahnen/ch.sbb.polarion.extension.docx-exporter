package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.Paragraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.paragraphs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Same check as {@link FormattingConversionTest}, but the expected model is not hand-coded: it is obtained
 * by parsing a committed reference DOCX ({@code /pandoc/docx/formattingTest.docx}) with the same
 * {@link DocxStructureInspector}. A fresh pandoc conversion of the HTML must parse to the identical
 * {@code Paragraph} sequence as that golden document.
 */
@SkipTestWhenParamNotSet
class FormattingConversionReferenceTest extends BasePandocTest {

    @Test
    void formattingMatchesReferenceDocx() throws Exception {
        List<Paragraph> expected = paragraphs(readDocxResource("formattingTest"));

        byte[] docBytes = exportToDOCX(readHtmlResource("formattingTest"), readTemplate("reference_template"),
                PandocParams.builder().build());
        assertNotNull(docBytes);
        writeReportDocx("formattingTest_reference_generated", docBytes);

        assertEquals(expected, paragraphs(docBytes));
    }
}
