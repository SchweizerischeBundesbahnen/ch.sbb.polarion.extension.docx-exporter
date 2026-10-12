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

    @ParameterizedTest
    @CsvSource({
            "false, tableColumnWidths",
            "true, tableColumnWidthsPreserved",
    })
    @SneakyThrows
    void tableColumnWidths(boolean preserveTableStyles, String testName) {
        ExportParams params = ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation("PORTRAIT")
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

        List<List<Long>> shares = columnWidths(docx).stream().map(TableColumnWidthsTest::sharesInPercent).toList();
        assertEquals(10, shares.size());
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
        // Every cell is pinned to its columns, in dxa
        columnWidths(docx).forEach(table -> table.cells().forEach(row -> assertTrue(row.stream().allMatch(width -> width > 0), table.toString())));

        File docxFile = getTestFile();
        try {
            Files.write(docxFile.toPath(), docx);
            compareContentUsingReferenceImages(testName, exportToPDF(docxFile));
        } finally {
            docxFile.delete();
        }
    }

    /** The share of each column in the width of its table, in whole percent. */
    private static List<Long> sharesInPercent(ColumnWidths table) {
        long total = table.grid().stream().mapToLong(Long::longValue).sum();
        return table.grid().stream().map(width -> Math.round(100.0 * width / total)).toList();
    }
}
