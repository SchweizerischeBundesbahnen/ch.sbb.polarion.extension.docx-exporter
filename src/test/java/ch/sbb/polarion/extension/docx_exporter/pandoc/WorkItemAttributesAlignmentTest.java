package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.Cell;
import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.Table;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import com.polarion.alm.tracker.model.IModule;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.tables;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

/**
 * The default table style of the reference document bottom-aligns the first row of a table. A Work Item
 * attributes table whose first row is a multi-line one (Linked Work Items) must still be top-aligned.
 */
@SkipTestWhenParamNotSet
class WorkItemAttributesAlignmentTest extends BaseDocxConverterTest {

    private static final String LIVE_DOC_HTML = """
            <div class="polarion-dle-workitem-basic-0" title="Requirement: EL-1">
                <span class="polarion-dle-workitem-title">EL-1 - Requirement</span>
                <table class="polarion-dle-workitem-fields-end-table" style="width: 100%">
                    <tbody>
                    <tr>
                        <td class="polarion-dle-workitem-fields-end-table-label">Linked Work Items</td>
                        <td class="polarion-dle-workitem-fields-end-table-value">has parent<br/>is related to<br/>is implemented by</td>
                    </tr>
                    <tr>
                        <td class="polarion-dle-workitem-fields-end-table-label">Status</td>
                        <td class="polarion-dle-workitem-fields-end-table-value">Reviewed</td>
                    </tr>
                    </tbody>
                </table>
            </div>
            """;

    @Test
    @SneakyThrows
    void attributesTableCellsAreTopAligned() {
        ExportParams params = ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation("PORTRAIT")
                .paperSize("A4")
                .preserveTableStyles(true)
                .build();

        DocumentData<IModule> liveDoc = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title("Work Item Attributes Alignment Test")
                .content(LIVE_DOC_HTML)
                .lastRevision("1")
                .revisionPlaceholder("1")
                .build();
        documentDataFactoryMockedStatic.when(() ->
                DocumentDataFactory.getDocumentData(eq(params), anyBoolean())).thenReturn(liveDoc);

        byte[] docx = converter.convertToDocx(params);
        assertNotNull(docx);
        writeReportDocx("workItemAttributesAlignment_generated", docx);

        List<Table> docxTables = tables(docx);
        assertEquals(1, docxTables.size());
        List<String> vAligns = docxTables.get(0).rows().stream()
                .flatMap(List::stream)
                .map(Cell::vAlign)
                .toList();
        assertEquals(List.of("top", "top", "top", "top"), vAligns);
    }
}
