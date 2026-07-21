package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.Table;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.tables;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Same check as {@link TableSizesConversionTest}, but the expected model is obtained by parsing a committed
 * reference DOCX ({@code /pandoc/docx/tableSizes.docx}) with {@link DocxStructureInspector} rather than being
 * hand-coded. A fresh pandoc conversion of the HTML must parse to the identical {@code Table} structure
 * (widths, alignment, cells and merges) as that golden document.
 */
@SkipTestWhenParamNotSet
class TableSizesConversionReferenceTest extends BasePandocTest {

    @Test
    void tableSizesMatchReferenceDocx() throws Exception {
        List<Table> expected = tables(readDocxResource("tableSizes"));

        byte[] docBytes = exportToDOCX(readHtmlResource("tableSizesTest"), readTemplate("reference_template"),
                PandocParams.builder().build());
        assertNotNull(docBytes);
        writeReportDocx("tableSizes_reference_generated", docBytes);

        assertEquals(expected, tables(docBytes));
    }
}
