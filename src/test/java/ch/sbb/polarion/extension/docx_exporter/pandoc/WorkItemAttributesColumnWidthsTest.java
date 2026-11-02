package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.ColumnWidths;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import com.polarion.alm.tracker.model.IModule;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.columnWidths;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Every Work Item attributes table splits its width 20% / 80% between labels and values, whatever their text.
 * Without fixed widths Word sizes each table by its content, and consecutive tables get different columns.
 * The PDF pandoc-service renders from the DOCX shows the same split.
 */
@SkipTestWhenParamNotSet
@SuppressWarnings("ResultOfMethodCallIgnored")
class WorkItemAttributesColumnWidthsTest extends BaseDocxConverterTest {

    private static final String LIVE_DOC_HTML = """
            <div class="polarion-dle-workitem-basic-0" title="Requirement: EL-1">
                <span class="polarion-dle-workitem-title">EL-1 - Short attributes</span>
                <table class="polarion-dle-workitem-fields-end-table" style="width: 100%">
                    <tbody>
                    <tr>
                        <td class="polarion-dle-workitem-fields-end-table-label">Status</td>
                        <td class="polarion-dle-workitem-fields-end-table-value">Open</td>
                    </tr>
                    <tr>
                        <td class="polarion-dle-workitem-fields-end-table-label">Severity</td>
                        <td class="polarion-dle-workitem-fields-end-table-value">Must have</td>
                    </tr>
                    </tbody>
                </table>
            </div>
            <div class="polarion-dle-workitem-basic-0" title="Requirement: EL-2">
                <span class="polarion-dle-workitem-title">EL-2 - Long label</span>
                <table class="polarion-dle-workitem-fields-end-table" style="width: 100%">
                    <tbody>
                    <tr>
                        <td class="polarion-dle-workitem-fields-end-table-label">A rather long attribute name for this field</td>
                        <td class="polarion-dle-workitem-fields-end-table-value">x</td>
                    </tr>
                    </tbody>
                </table>
            </div>
            <div class="polarion-dle-workitem-basic-0" title="Requirement: EL-3">
                <span class="polarion-dle-workitem-title">EL-3 - Long value</span>
                <table class="polarion-dle-workitem-fields-end-table" style="width: 100%">
                    <tbody>
                    <tr>
                        <td class="polarion-dle-workitem-fields-end-table-label">Linked</td>
                        <td class="polarion-dle-workitem-fields-end-table-value">relates to: EL-100 A very long linked work item title that goes on and on, EL-101 Another one, EL-102 And another</td>
                    </tr>
                    </tbody>
                </table>
            </div>
            """;

    @Test
    @SneakyThrows
    void attributesTablesHaveFixedColumnWidths() {
        ExportParams params = ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation("PORTRAIT")
                .paperSize("A4")
                .build();

        DocumentData<IModule> liveDoc = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title("Work Item Attributes Column Widths Test")
                .content(LIVE_DOC_HTML)
                .lastRevision("1")
                .revisionPlaceholder("1")
                .build();
        documentDataFactoryMockedStatic.when(() ->
                DocumentDataFactory.getDocumentData(eq(params), anyBoolean())).thenReturn(liveDoc);

        byte[] docx = converter.convertToDocx(params);
        assertNotNull(docx);
        writeReportDocx("workItemAttributesColumnWidths_generated", docx);

        List<ColumnWidths> tables = columnWidths(docx);
        assertEquals(3, tables.size());
        List<Long> grid = tables.get(0).grid();
        assertEquals(2, grid.size());
        assertEquals(0.2, (double) grid.get(0) / (grid.get(0) + grid.get(1)), 0.005);
        for (ColumnWidths table : tables) {
            // the same grid in every table, and every cell pinned to its column
            assertEquals(grid, table.grid());
            table.cells().forEach(row -> assertEquals(grid, row));
        }

        File docxFile = getTestFile();
        try {
            Files.write(docxFile.toPath(), docx);
            compareContentUsingReferenceImages("workItemAttributesColumnWidths", exportToPDF(docxFile));
        } finally {
            docxFile.delete();
        }
    }
}
