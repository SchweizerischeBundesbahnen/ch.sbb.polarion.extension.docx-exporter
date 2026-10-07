package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.PictureLayout;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import com.polarion.alm.tracker.model.IModule;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.pictureLayouts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Enum icons and the icons of linked documents, centered on their text with a 2px gap, as pdf-exporter draws them.
 * pandoc-service lowers them with {@code <w:position>} and spaces them with {@code <wp:effectExtent>}, and keeps both
 * in the PDF it renders from the DOCX.
 */
@SkipTestWhenParamNotSet
@SuppressWarnings("ResultOfMethodCallIgnored")
class IconAlignmentTest extends BaseDocxConverterTest {

    // A 16px icon beside 12pt text: a quarter of 12pt up, half of the icon's 12pt down, in half-points
    private static final int CENTERED_ON_12PT_TEXT = -6;
    // 2px at 96 dpi
    private static final long ICON_GAP = 2 * 9525L;

    @Test
    @SneakyThrows
    void iconAlignment() {
        ExportParams params = ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation("PORTRAIT")
                .paperSize("A4")
                .build();

        DocumentData<IModule> liveDoc = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title("Icon Alignment Test")
                .content(readHtmlResource("iconAlignment"))
                .lastRevision("1")
                .revisionPlaceholder("1")
                .build();
        documentDataFactoryMockedStatic.when(() ->
                DocumentDataFactory.getDocumentData(eq(params), anyBoolean())).thenReturn(liveDoc);

        byte[] docx = converter.convertToDocx(params);
        assertNotNull(docx);
        writeReportDocx("iconAlignment_generated", docx);

        // Three enum icons, the icon of a linked page and those of two linked documents
        assertEquals(Collections.nCopies(6, new PictureLayout(CENTERED_ON_12PT_TEXT, 0, ICON_GAP)), pictureLayouts(docx));

        File docxFile = getTestFile();
        try {
            Files.write(docxFile.toPath(), docx);
            compareContentUsingReferenceImages("iconAlignment", exportToPDF(docxFile));
        } finally {
            docxFile.delete();
        }
    }
}
