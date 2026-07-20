package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.Extent;
import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.Paragraph;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.paragraphs;
import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test which checks image sizes.
 * <p>
 * The source Polarion LiveDoc shows the same 182x97 PNG three times: resized to 150% (273x145),
 * at its original/intrinsic size (182x97) and resized to 50% (91x48). Instead of pixel-diffing rendered
 * pages, this asserts the exact heading -&gt; image sequence and the exact drawing extents (EMU = px x 9525)
 * pandoc-service produces in {@code word/document.xml}.
 */
@SkipTestWhenParamNotSet
class ImageSizesConversionTest extends BasePandocTest {

    @Test
    void imageSizesAreConvertedToDocx() throws Exception {
        byte[] docBytes = exportToDOCX(buildHtml(), readTemplate("reference_template"), PandocParams.builder().build());
        assertNotNull(docBytes);
        writeReportDocx("imageSizes_generated", docBytes);

        List<Paragraph> expected = List.of(
                new Paragraph("Title", List.of(plain("Image Sizes Test")), List.of()),
                new Paragraph("Heading1", List.of(plain("Image Sizes Test")), List.of()),
                new Paragraph("Heading2", List.of(plain("Resized to 150%")), List.of()),
                new Paragraph("BodyText", List.of(), List.of(new Extent(2600325, 1381125))), // 273 x 145 px
                new Paragraph("Heading2", List.of(plain("Original size")), List.of()),
                new Paragraph("BodyText", List.of(), List.of(new Extent(1733550, 923925))),  // 182 x 97 px
                new Paragraph("Heading2", List.of(plain("Resized to 50%")), List.of()),
                new Paragraph("BodyText", List.of(), List.of(new Extent(866775, 457200))));  // 91 x 48 px

        assertEquals(expected, paragraphs(docBytes));
    }

    private String buildHtml() throws IOException {
        String base64;
        try (InputStream inputStream = readPngResource("imageSizes")) {
            base64 = Base64.getEncoder().encodeToString(inputStream.readAllBytes());
        }
        String dataUrl = "data:image/png;base64," + base64;
        return "<!DOCTYPE html><html><head><title>Image Sizes Test</title></head><body>"
                + "<h1>Image Sizes Test</h1>"
                + "<h2>Resized to 150%</h2>"
                + "<p><img src=\"" + dataUrl + "\" style=\"width: 273px;height: 145px;\"/></p>"
                + "<h2>Original size</h2>"
                + "<p><img src=\"" + dataUrl + "\" style=\"max-width: 650px;\"/></p>"
                + "<h2>Resized to 50%</h2>"
                + "<p><img src=\"" + dataUrl + "\" style=\"width: 91px;height: 48px;\"/></p>"
                + "</body></html>";
    }
}
