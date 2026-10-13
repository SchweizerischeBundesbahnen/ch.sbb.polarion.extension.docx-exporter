package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.ColumnWidths;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import com.polarion.alm.tracker.model.IModule;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.columnWidths;
import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.pictureWidths;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Tables of a Live Document with the column widths they state, and without. A column without a stated
 * width takes the room its content needs, as in a browser, and a stated width is kept. Both settings
 * of "Preserve table styles" are compared, since the cells carry Polarion's borders.
 */
@SkipTestWhenParamNotSet
@SuppressWarnings("ResultOfMethodCallIgnored")
class TableColumnWidthsTest extends BaseDocxConverterTest {

    private static final long EMU_PER_PIXEL = 9525;

    @ParameterizedTest
    @CsvSource({
            "false, PORTRAIT, tableColumnWidths",
            "true, PORTRAIT, tableColumnWidthsPreserved",
            "false, LANDSCAPE, tableColumnWidthsLandscape",
    })
    @SneakyThrows
    void tableColumnWidths(boolean preserveTableStyles, String orientation, String testName) {
        ExportParams params = ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation(orientation)
                .paperSize("A4")
                .preserveTableStyles(preserveTableStyles)
                .build();

        DocumentData<IModule> liveDoc = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title("Table Column Widths Test")
                .content(readHtmlResource("tableColumnWidths"))
                .lastRevision("1")
                .revisionPlaceholder("1")
                .build();
        documentDataFactoryMockedStatic.when(() ->
                DocumentDataFactory.getDocumentData(eq(params), anyBoolean())).thenReturn(liveDoc);

        byte[] docx = converter.convertToDocx(params);
        assertNotNull(docx);
        writeReportDocx(testName + "_generated", docx);

        List<ColumnWidths> tables = columnWidths(docx);
        List<List<Long>> shares = tables.stream().map(TableColumnWidthsTest::sharesInPercent).toList();
        assertEquals(19, shares.size()); // 18 tables and the one nested in table 18
        // Columns without a stated width take what their content needs, a line break or not
        assertTrue(shares.get(0).get(0) < 15, "automatic widths: " + shares.get(0));
        assertTrue(shares.get(1).get(0) < 15, "automatic widths, a line break in a cell: " + shares.get(1));
        // Stated widths are kept
        assertEquals(List.of(30L, 70L), shares.get(2));
        assertEquals(List.of(25L, 25L, 50L), shares.get(3));
        // Pixel widths are replaced by automatic ones, so the long title takes the room
        assertTrue(shares.get(4).get(0) < 15 && shares.get(4).get(2) < 15, "widths in pixels: " + shares.get(4));
        assertEquals(15L, shares.get(5).get(0));
        assertTrue(shares.get(5).get(1) > 60, "one column in percent: " + shares.get(5));
        assertTrue(shares.get(6).get(2) > 60, "a header cell spanning two columns: " + shares.get(6));
        assertTrue(shares.get(7).get(0) < 30, "a table of half the width: " + shares.get(7));
        assertEquals(List.of(25L, 25L, 25L, 25L), shares.get(8));
        assertTrue(shares.get(9).get(0) < 15, "a long word that does not break: " + shares.get(9));
        assertTrue(shares.get(10).get(2) > 50, "cells merged across rows: " + shares.get(10));
        // The image keeps its 160 px, as a browser keeps it, and the description takes the rest
        assertEquals(List.of(160L * EMU_PER_PIXEL), pictureWidths(docx));
        assertTrue(shares.get(11).get(1) > 50, "an image in a cell: " + shares.get(11));
        assertTrue(shares.get(12).size() == 10 && shares.get(12).stream().allMatch(share -> share >= 8 && share <= 14), "ten narrow columns: " + shares.get(12));
        // 300 px are 4500 twips, and 60 % of the table of the full width
        assertEquals(4500, totalWidth(tables.get(13)), 15, "a table of 300 px: " + tables.get(13).grid());
        assertEquals(0.6, (double) totalWidth(tables.get(14)) / totalWidth(tables.get(0)), 0.01, "a table of 60 %");
        assertTrue(shares.get(15).get(1) < 15, "an empty column: " + shares.get(15));
        assertEquals(40L, shares.get(16).get(0));
        // The cell holding a nested table takes the room, and the nested table fits it
        assertTrue(shares.get(17).get(1) > 50, "a table nested in a cell: " + shares.get(17));
        assertTrue(totalWidth(tables.get(18)) <= tables.get(17).grid().get(1), "the nested table fits its cell: " + tables.get(18).grid());
        // Every cell is pinned in dxa to the columns it spans, a spanning header cell to the sum of them
        tables.forEach(table -> assertEquals(table.spannedGridWidths(), table.cells(), table.toString()));

        File docxFile = getTestFile();
        try {
            Files.write(docxFile.toPath(), docx);
            compareContentUsingReferenceImages(testName, exportToPDF(docxFile));
        } finally {
            docxFile.delete();
        }
    }

    private static long totalWidth(ColumnWidths table) {
        return table.grid().stream().mapToLong(Long::longValue).sum();
    }

    /** The share of each column in the width of its table, in whole percent. */
    private static List<Long> sharesInPercent(ColumnWidths table) {
        long total = table.grid().stream().mapToLong(Long::longValue).sum();
        return table.grid().stream().map(width -> Math.round(100.0 * width / total)).toList();
    }
}
