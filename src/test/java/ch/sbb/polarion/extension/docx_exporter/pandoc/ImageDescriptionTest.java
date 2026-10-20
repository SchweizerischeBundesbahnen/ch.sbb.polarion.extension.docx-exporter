package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.PictureDescription;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.documentXml;
import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.pictureDescriptions;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Integration test which checks the descriptions of the pictures of a DOCX.
 * <p>
 * pandoc-service hands the {@code data:} images of the HTML to pandoc as files, and pandoc writes the source of a picture
 * into {@code descr} of its {@code <pic:cNvPr>}. That source was the name of the file, with a token which differs in
 * every conversion. pandoc-service leaves that description empty, so the alt text of {@code <wp:docPr>} is the only one
 * a picture carries, and two conversions of one document give the same {@code word/document.xml}.
 */
@SkipTestWhenParamNotSet
class ImageDescriptionTest extends BasePandocTest {

    private static final String EXTRACTED_IMAGE_NAME = "pandoc-service-image-";

    @Test
    void pictureCarriesAltTextOnly() throws Exception {
        byte[] docBytes = exportToDOCX(buildHtml(), readTemplate("reference_template"), PandocParams.builder().build());
        writeReportDocx("imageDescription_generated", docBytes);

        List<PictureDescription> expected = List.of(
                new PictureDescription("", ""),
                new PictureDescription("Image with alt text", ""),
                new PictureDescription("", ""));

        assertEquals(expected, pictureDescriptions(docBytes));
        assertFalse(new String(documentXml(docBytes), StandardCharsets.UTF_8).contains(EXTRACTED_IMAGE_NAME));
    }

    @Test
    void sameDocumentGivesSameDocumentXml() throws Exception {
        String html = buildHtml();

        byte[] first = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().build());
        byte[] second = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().build());

        assertArrayEquals(documentXml(first), documentXml(second));
    }

    static String buildHtml() throws IOException {
        String base64;
        try (InputStream inputStream = readPngResource("imageSizes")) {
            base64 = Base64.getEncoder().encodeToString(inputStream.readAllBytes());
        }
        String dataUrl = "data:image/png;base64," + base64;
        return "<!DOCTYPE html><html><head><title>Image Description Test</title></head><body>"
                + "<h1>Image Description Test</h1>"
                + "<p><img src=\"" + dataUrl + "\"/></p>"
                + "<p><img src=\"" + dataUrl + "\" alt=\"Image with alt text\"/></p>"
                + "<table><tr><td><img src=\"" + dataUrl + "\"/></td></tr></table>"
                + "</body></html>";
    }
}
