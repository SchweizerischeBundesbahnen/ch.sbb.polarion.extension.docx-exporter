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
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.pictureLayouts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Font Awesome icons drawn in their color in front of their text, as pdf-exporter draws them: the plan icon of a work item,
 * then icons of the solid and regular styles, of several widths and of colors given in several ways. pandoc-service rasterizes the SVG each
 * is replaced with, and the picture is centered on its text like the enum icons beside it.
 */
@SkipTestWhenParamNotSet
@SuppressWarnings("ResultOfMethodCallIgnored")
class FontAwesomeIconTest extends BaseDocxConverterTest {

    // A 16px icon beside 12pt text: a quarter of 12pt up, half of the icon's 12pt down, in half-points
    private static final int CENTERED_ON_12PT_TEXT = -6;
    // 2px at 96 dpi
    private static final long ICON_GAP = 2 * 9525L;
    private static final String FONT_AWESOME_SVGS_PATH = "/polarion/ria/fontawesome-6.2.0/svgs/";

    @Test
    @SneakyThrows
    void fontAwesomeIcon() {
        when(fileResourceProvider.getResourceAsBytes(anyString())).thenAnswer(invocation -> readSvgResource(invocation.getArgument(0)));

        ExportParams params = ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation("PORTRAIT")
                .paperSize("A4")
                .build();

        DocumentData<IModule> liveDoc = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title("Font Awesome Icon Test")
                .content(readHtmlResource("fontAwesomeIcon"))
                .lastRevision("1")
                .revisionPlaceholder("1")
                .build();
        documentDataFactoryMockedStatic.when(() ->
                DocumentDataFactory.getDocumentData(eq(params), anyBoolean())).thenReturn(liveDoc);

        byte[] docx = converter.convertToDocx(params);
        assertNotNull(docx);
        writeReportDocx("fontAwesomeIcon_generated", docx);

        // The severity, status and plan icons of the work item, then the icons of the variations, the unknown one left out
        assertEquals(Collections.nCopies(23, new PictureLayout(CENTERED_ON_12PT_TEXT, 0, ICON_GAP)), pictureLayouts(docx));

        File docxFile = getTestFile();
        try {
            Files.write(docxFile.toPath(), docx);
            compareContentUsingReferenceImages("fontAwesomeIcon", exportToPDF(docxFile));
        } finally {
            docxFile.delete();
        }
    }

    @SneakyThrows
    private static byte[] readSvgResource(String url) {
        try (InputStream svg = FontAwesomeIconTest.class.getResourceAsStream(url.replace(FONT_AWESOME_SVGS_PATH, "/pandoc/svg/"))) {
            return svg == null || !url.startsWith(FONT_AWESOME_SVGS_PATH) ? new byte[0] : svg.readAllBytes();
        }
    }
}
