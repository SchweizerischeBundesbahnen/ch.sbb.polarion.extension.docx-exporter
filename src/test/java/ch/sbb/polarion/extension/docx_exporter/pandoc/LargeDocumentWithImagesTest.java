package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import lombok.SneakyThrows;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for large documents (10+ MB) containing multiple PNG images.
 * This test verifies that the DOCX exporter correctly handles large HTML documents
 * with many embedded base64-encoded PNG images.
 */
@SkipTestWhenParamNotSet
@SuppressWarnings("ResultOfMethodCallIgnored")
class LargeDocumentWithImagesTest extends BasePandocTest {

    private static final int MIN_HTML_SIZE_MB = 10;
    private static final int MIN_HTML_SIZE_BYTES = MIN_HTML_SIZE_MB * 1024 * 1024;
    private static final int IMAGE_WIDTH = 400;
    private static final int IMAGE_HEIGHT = 300;
    private static final int IMAGES_PER_SECTION = 3;

    @Test
    @SneakyThrows
    void shouldConvertLargeDocumentWithMultiplePngImages() {
        String html = generateLargeHtmlWithImages();
        assertTrue(html.length() >= MIN_HTML_SIZE_BYTES, String.format("Generated HTML should be at least %d MB, but was %.2f MB", MIN_HTML_SIZE_MB, html.length() / (1024.0 * 1024.0)));

        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"), null, PandocParams.builder().build());

        assertNotNull(docBytes, "Generated DOCX should not be null");
        assertTrue(docBytes.length > 0, "Generated DOCX should have content");

        writeReportDocx("testLargeDocumentWithImages_generated", docBytes);

        // Verify the document can be loaded and contains images
        WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docBytes));
        assertNotNull(wordMLPackage, "WordprocessingMLPackage should be loadable");

        // Count embedded images
        List<Part> imageParts = wordMLPackage.getParts().getParts().values().stream()
                .filter(BinaryPartAbstractImage.class::isInstance)
                .toList();

        assertFalse(imageParts.isEmpty(), "Document should contain embedded images");
    }

    @Test
    @SneakyThrows
    void shouldConvertLargeDocumentWithImagesToValidPdf() {
        String html = generateLargeHtmlWithImages();
        assertTrue(html.length() >= MIN_HTML_SIZE_BYTES,
                String.format("Generated HTML should be at least %d MB", MIN_HTML_SIZE_MB));

        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"), null, PandocParams.builder().build());
        assertNotNull(docBytes, "Generated DOCX should not be null");
        writeReportDocx("testLargeDocumentWithImages_for_pdf_generated", docBytes);

        File tempFile = createTempDocxFile(docBytes);
        try {
            byte[] pdfBytes = exportToPDF(tempFile);
            assertNotNull(pdfBytes, "Generated PDF should not be null");
            assertTrue(pdfBytes.length > 0, "Generated PDF should have content");

            writeReportPdf("testLargeDocumentWithImages", "generated", pdfBytes);

            // Verify PDF has expected number of pages
            try (PDDocument pdfDoc = Loader.loadPDF(pdfBytes)) {
                assertTrue(pdfDoc.getNumberOfPages() >= 1, "PDF should have at least 1 page");
            }
        } finally {
            tempFile.delete();
        }
    }

    @Test
    @SneakyThrows
    void shouldConvertLargeDocumentWithImagesAndLandscapeOrientation() {
        String html = generateLargeHtmlWithImages();
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"), null,
                PandocParams.builder().orientation("landscape").build());

        assertNotNull(docBytes, "Generated DOCX should not be null");
        writeReportDocx("testLargeDocumentWithImages_landscape_generated", docBytes);

        WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docBytes));
        assertNotNull(wordMLPackage, "WordprocessingMLPackage should be loadable");

        // Verify images are still present in landscape mode
        List<Part> imageParts = wordMLPackage.getParts().getParts().values().stream()
                .filter(BinaryPartAbstractImage.class::isInstance)
                .toList();

        assertTrue(imageParts.size() > 0, "Document in landscape mode should contain images");
    }

    @Test
    @SneakyThrows
    void shouldConvertLargeDocumentWithImagesAndTableOfContents() {
        String html = generateLargeHtmlWithImages();
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"),
                List.of("--toc"), PandocParams.builder().build());

        assertNotNull(docBytes, "Generated DOCX with ToC should not be null");
        writeReportDocx("testLargeDocumentWithImages_with_toc_generated", docBytes);

        WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docBytes));
        assertNotNull(wordMLPackage, "WordprocessingMLPackage with ToC should be loadable");

        // Verify images are present even with ToC option
        List<Part> imageParts = wordMLPackage.getParts().getParts().values().stream()
                .filter(BinaryPartAbstractImage.class::isInstance)
                .toList();

        assertTrue(imageParts.size() > 0, "Document with ToC should contain images");
    }

    @Test
    @SneakyThrows
    void shouldConvertLargeDocumentWithImagesAndA3PaperSize() {
        String html = generateLargeHtmlWithImages();
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"), null,
                PandocParams.builder().paperSize("A3").build());

        assertNotNull(docBytes, "Generated DOCX with A3 paper should not be null");
        writeReportDocx("testLargeDocumentWithImages_a3_paper_generated", docBytes);

        WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docBytes));
        assertNotNull(wordMLPackage, "WordprocessingMLPackage with A3 should be loadable");

        // Verify images are present with different paper size
        List<Part> imageParts = wordMLPackage.getParts().getParts().values().stream()
                .filter(BinaryPartAbstractImage.class::isInstance)
                .toList();

        assertTrue(imageParts.size() > 0, "Document with A3 paper size should contain images");
    }

    /**
     * Generates a large HTML document (10+ MB) with embedded PNG images.
     * Each section contains multiple images with random colored patterns.
     */
    private String generateLargeHtmlWithImages() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <title>Large Document With Images Test</title>\n");
        html.append("    <meta content=\"text/html; charset=UTF-8\" http-equiv=\"Content-Type\"/>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("<h1>Large Document With Multiple PNG Images (10+ MB)</h1>\n");
        html.append("<p>This document tests the conversion of a large document containing multiple PNG images.</p>\n");

        Random random = new Random(42); // Fixed seed for reproducibility
        int sectionNumber = 0;

        // Generate sections until we reach the target size
        while (html.length() < MIN_HTML_SIZE_BYTES) {
            sectionNumber++;
            html.append(generateSection(sectionNumber, random));
        }

        html.append("<h2>Conclusion</h2>\n");
        html.append("<p>This document contains ").append(sectionNumber).append(" sections with embedded PNG images.</p>\n");
        html.append("<p>Total HTML size: ").append(String.format("%.2f", html.length() / (1024.0 * 1024.0))).append(" MB</p>\n");
        html.append("</body>\n");
        html.append("</html>");

        return html.toString();
    }

    /**
     * Generates a single section with heading, text content, and embedded images.
     */
    private String generateSection(int sectionNumber, Random random) {
        StringBuilder section = new StringBuilder();
        section.append("<h2>Section ").append(sectionNumber).append(": Test Content</h2>\n");
        section.append("<p>This is section ").append(sectionNumber).append(" containing multiple embedded PNG images. ");
        section.append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ");
        section.append("ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ");
        section.append("ullamco laboris nisi ut aliquip ex ea commodo consequat.</p>\n");

        // Add images to this section
        for (int i = 0; i < IMAGES_PER_SECTION; i++) {
            String base64Image = generateRandomPngBase64(random);
            section.append("<p><img src=\"data:image/png;base64,").append(base64Image);
            section.append("\" alt=\"Image ").append(sectionNumber).append("-").append(i + 1);
            section.append("\" title=\"section").append(sectionNumber).append("_image").append(i + 1).append(".png\"");
            section.append(" style=\"width: ").append(IMAGE_WIDTH).append("px; height: ").append(IMAGE_HEIGHT).append("px;\"/></p>\n");
        }

        // Add a table with an image
        section.append("<table border=\"1\">\n");
        section.append("    <tr><th>Property</th><th>Value</th><th>Image</th></tr>\n");
        section.append("    <tr><td>Section</td><td>").append(sectionNumber).append("</td>");
        section.append("<td><img src=\"data:image/png;base64,").append(generateRandomPngBase64(random));
        section.append("\" alt=\"Table Image\" style=\"width: 100px; height: 75px;\"/></td></tr>\n");
        section.append("</table>\n");

        return section.toString();
    }

    /**
     * Generates a random PNG image and returns it as a base64 encoded string.
     * The image contains random colored rectangles to ensure varied content.
     */
    @SneakyThrows
    private String generateRandomPngBase64(Random random) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Fill background with a random light color
        g2d.setColor(new Color(200 + random.nextInt(56), 200 + random.nextInt(56), 200 + random.nextInt(56)));
        g2d.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        // Draw random colored rectangles
        int numRectangles = 10 + random.nextInt(20);
        for (int i = 0; i < numRectangles; i++) {
            g2d.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            int x = random.nextInt(IMAGE_WIDTH);
            int y = random.nextInt(IMAGE_HEIGHT);
            int w = 20 + random.nextInt(100);
            int h = 20 + random.nextInt(80);
            g2d.fillRect(x, y, w, h);
        }

        // Draw some circles
        int numCircles = 5 + random.nextInt(10);
        for (int i = 0; i < numCircles; i++) {
            g2d.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            int x = random.nextInt(IMAGE_WIDTH);
            int y = random.nextInt(IMAGE_HEIGHT);
            int diameter = 20 + random.nextInt(60);
            g2d.fillOval(x, y, diameter, diameter);
        }

        // Draw border
        g2d.setColor(Color.BLACK);
        g2d.drawRect(0, 0, IMAGE_WIDTH - 1, IMAGE_HEIGHT - 1);

        g2d.dispose();

        // Convert to PNG bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();

        return Base64.getEncoder().encodeToString(imageBytes);
    }

    private File createTempDocxFile(byte[] docBytes) throws Exception {
        Path tempPath = Files.createTempFile("test_large_images", ".docx");
        File tempFile = tempPath.toFile();
        tempFile.deleteOnExit();
        Files.write(tempPath, docBytes);
        return tempFile;
    }
}
