package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.Cell;
import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.Table;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.tables;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test mirroring the {@code test_table_sizes.py} system test.
 * <p>
 * The source Polarion LiveDoc contains nine tables exercising relative widths (100% / 80% / 40% / 25%),
 * fixed pixel widths (50px / 100px), vertical cell merges (rowspan) and left/center/right table alignment.
 * Instead of pixel-diffing rendered pages, this asserts the exact per-table structure pandoc-service
 * produces in {@code word/document.xml}: width type+value, alignment, and every cell's text and
 * {@code vMerge}/{@code gridSpan} merge markers.
 */
@SkipTestWhenParamNotSet
class TableSizesConversionTest extends BasePandocTest {

    @Test
    void tableSizesAreConvertedToDocx() throws Exception {
        byte[] docBytes = exportToDOCX(readHtmlResource("tableSizesTest"), readTemplate("reference_template"),
                PandocParams.builder().build());
        assertNotNull(docBytes);
        writeReportDocx("tableSizes_generated", docBytes);

        List<Table> expected = List.of(
                // Table 1 — 100% width.
                new Table("pct", 5000, "left", List.of(
                        List.of(c("Cell 1"), c("Cell 2"), c("Cell 3")),
                        List.of(c("Cell 1.1"), c("Cell 2.1"), c("Cell 3.1")),
                        List.of(c("Cell 1.2"), c("Cell 2.2"), c("Cell 3.2")))),
                // Table 2 — 100% width with vertical (rowspan) merges.
                new Table("pct", 5000, "left", List.of(
                        List.of(c("Cell 1"), c("Cell 2"), c("Cell 3")),
                        List.of(merge("Vertical Merge"), merge("Horizontal Merge"), c("Cell 3.1")),
                        List.of(cont(), cont(), c("Cell 3.2")),
                        List.of(c("Cell 1.3"), cont(), c("Cell 3.3")),
                        List.of(c("Cell 1.4"), c("Cell 2.4"), c("Cell 3.4")))),
                // Table 3 — 40% width.
                new Table("pct", 2000, "left", List.of(
                        List.of(c("Cell 1")),
                        List.of(c("Cell 1.1")))),
                // Table 4 — 80% width.
                new Table("pct", 4000, "left", List.of(
                        List.of(c("Cell 1")),
                        List.of(c("Cell 1.1")))),
                // Table 5 — fixed 50px width.
                new Table("dxa", 750, "left", List.of(
                        List.of(c("1"), c("2"), c("3")),
                        List.of(c("a"), c("b"), c("c")),
                        List.of(c("d"), c("e"), c("f")))),
                // Table 6 — fixed 100px width.
                new Table("dxa", 1500, "left", List.of(
                        List.of(c("1"), c("2"), c("3")),
                        List.of(c("a"), c("b"), c("c")),
                        List.of(c("D"), c("E"), c("F")))),
                // Table 7 — 25% width, left aligned.
                new Table("pct", 1250, "left", List.of(
                        List.of(c("Cell 1")),
                        List.of(c("Cell 1.1")))),
                // Table 8 — 25% width, center aligned.
                new Table("pct", 1250, "center", List.of(
                        List.of(c("Cell 1")),
                        List.of(c("Cell 1.1")))),
                // Table 9 — 25% width, right aligned.
                new Table("pct", 1250, "right", List.of(
                        List.of(c("Cell 1")),
                        List.of(c("Cell 1.1")))));

        assertEquals(expected, tables(docBytes));
    }

    /** A plain cell. */
    private static Cell c(String text) {
        return new Cell(text, null, null);
    }

    /** A cell that starts a vertical (rowspan) merge. */
    private static Cell merge(String text) {
        return new Cell(text, "restart", null);
    }

    /** A cell that continues a vertical merge from the row above. */
    private static Cell cont() {
        return new Cell("", "continue", 1);
    }
}
