package ch.sbb.polarion.extension.docx_exporter.pandoc;

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
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

/**
 * What a document looks like when the style of an element names a resource which cannot be checked.
 * <p>
 * The whole pipeline runs: the extension rewrites the html, pandoc converts it to DOCX, the service renders
 * that to PDF and the pages are compared with the references. The style attribute of the image carries its
 * size and an address nothing in it accounts for, and the image itself is a data url. Only the address may
 * go: the size around it is what places the image on the page, and the data url carries the image. Such an
 * attribute used to be emptied whole, which left the image at its intrinsic size.
 * </p>
 */
@SkipTestWhenParamNotSet
class BlockedResourceStylesTest extends BaseDocxConverterTest {

    /** A url the parser reads as no url term of its own: one written without quotes ends at the bracket in it. */
    private static final String ADDRESS_NOTHING_ACCOUNTS_FOR = "background: url(https://images.example.com/x.png?a=(b));";

    @Test
    @SneakyThrows
    void stylesApplyAlthoughOneOfThemNamesAnAddressWhichCannotBeChecked() {
        ExportParams params = ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation("PORTRAIT")
                .paperSize("A4")
                .build();

        DocumentData<IModule> liveDoc = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title("A style naming a resource which cannot be checked")
                .content(buildHtml())
                .lastRevision("1")
                .revisionPlaceholder("1")
                .build();
        documentDataFactoryMockedStatic.when(() ->
                DocumentDataFactory.getDocumentData(eq(params), anyBoolean())).thenReturn(liveDoc);

        byte[] docx = converter.convertToDocx(params);
        assertNotNull(docx);
        writeReportDocx("blockedResourceStyles_generated", docx);

        File docxFile = getTestFile();
        try {
            Files.write(docxFile.toPath(), docx);
            compareContentUsingReferenceImages("blockedResourceStyles", exportToPDF(docxFile));
        } finally {
            docxFile.delete();
        }
    }

    /**
     * The same 182x97 image {@link ImageSizesConversionTest} uses, shown at 150% and at half its size. Each
     * size stands in a style attribute which also names the address nothing accounts for, so the page shows
     * what survived that: two images of different sizes, and the address in neither.
     */
    @SneakyThrows
    private static String buildHtml() {
        String base64;
        try (InputStream inputStream = readPngResource("imageSizes")) {
            base64 = Base64.getEncoder().encodeToString(inputStream.readAllBytes());
        }
        String dataUrl = "data:image/png;base64," + base64;
        return "<!DOCTYPE html><html><head><title>A style naming a resource which cannot be checked</title></head><body>"
                + "<h1>A style naming a resource which cannot be checked</h1>"
                + "<p>Each image below is sized by a style attribute which also says "
                + "background: url(https://images.example.com/x.png?a=(b)), which the CSS parser reads as no url "
                + "term of its own. That address is replaced where it stands, and the size around it and the data "
                + "url of the image itself have to survive it.</p>"
                + "<h2>Resized to 150%</h2>"
                + "<p><img src=\"" + dataUrl + "\" style=\"width: 273px;height: 145px;" + ADDRESS_NOTHING_ACCOUNTS_FOR + "\"/></p>"
                + "<h2>Resized to 50%</h2>"
                + "<p><img src=\"" + dataUrl + "\" style=\"width: 91px;height: 48px;" + ADDRESS_NOTHING_ACCOUNTS_FOR + "\"/></p>"
                + "</body></html>";
    }
}
