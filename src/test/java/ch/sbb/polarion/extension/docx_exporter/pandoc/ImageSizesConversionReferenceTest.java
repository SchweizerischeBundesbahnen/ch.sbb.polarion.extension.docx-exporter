package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.Paragraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.paragraphs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Same check as {@link ImageSizesConversionTest}, but the expected model is obtained by parsing a committed
 * reference DOCX ({@code /pandoc/docx/imageSizes.docx}) with {@link DocxStructureInspector} rather than being
 * hand-coded. A fresh pandoc conversion of the HTML must parse to the identical heading -&gt; image sequence
 * (including drawing extents) as that golden document.
 */
@SkipTestWhenParamNotSet
class ImageSizesConversionReferenceTest extends BasePandocTest {

    @Test
    void imageSizesMatchReferenceDocx() throws Exception {
        List<Paragraph> expected = paragraphs(readDocxResource("imageSizes"));

        byte[] docBytes = exportToDOCX(ImageSizesConversionTest.buildHtml(), readTemplate("reference_template"),
                PandocParams.builder().build());
        assertNotNull(docBytes);
        writeReportDocx("imageSizes_reference_generated", docBytes);

        assertEquals(expected, paragraphs(docBytes));
    }
}
