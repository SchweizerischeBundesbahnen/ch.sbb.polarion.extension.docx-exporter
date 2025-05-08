package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.util.MediaUtils;
import com.google.gwt.thirdparty.guava.common.io.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SkipTestWhenParamNotSet
class BasicTest extends BasePandocTest {

    @Test
    void testInvalidTemplate() throws Exception {
        Exception exception = null;
        try {
            exportToDOCX(readHtmlResource(getCurrentMethodName()), readTemplate("invalid_template"));
        } catch (Exception e) {
            exception = e;
        }
        assertNotNull(exception, "Expected an exception for invalid template");
    }

    @Test
    void testLargeDocument() throws Exception {
        String largeHtml = generateLargeHtmlContent();
        byte[] doc = exportToDOCX(largeHtml, readTemplate("reference_template"));
        assertNotNull(doc);
        File newFile = File.createTempFile("test", ".docx");
        newFile.deleteOnExit();
        try {
            Files.write(doc, newFile);
            byte[] pdf = exportToPDF(newFile);
            assertNotNull(pdf);
            compareContentUsingReferenceImages(getCurrentMethodName(), pdf);
        } finally {
            newFile.delete();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"testSimple", "testTable", "testLists", "testParagraphs"})
    void runTest(String testName) throws Exception {
        byte[] doc = exportToDOCX(readHtmlResource(testName), readTemplate("reference_template"));
        assertNotNull(doc);
        File newFile = File.createTempFile("test", ".docx");
        newFile.deleteOnExit();
        try {
            Files.write(doc, newFile);
            byte[] pdf = exportToPDF(newFile);
            assertNotNull(pdf);
            compareContentUsingReferenceImages(testName, pdf);
        } finally {
            newFile.delete();
        }
    }

    private void compareContentUsingReferenceImages(String testName, byte[] pdf) throws IOException {
        writeReportPdf(testName, "generated", pdf);
        List<BufferedImage> resultImages = getAllPagesAsImagesAndLogAsReports(testName, pdf);
        boolean hasDiff = false;
        for (int i = 0; i < resultImages.size(); i++) {
            try (InputStream inputStream = readPngResource(testName + PAGE_SUFFIX + i)) {
                BufferedImage expectedImage = ImageIO.read(inputStream);
                BufferedImage resultImage = resultImages.get(i);
                List<Point> diffPoints = MediaUtils.diffImages(expectedImage, resultImage);
                if (!diffPoints.isEmpty()) {
                    MediaUtils.fillImagePoints(resultImage, diffPoints, Color.BLUE.getRGB());
                    writeReportImage(String.format("%s%s%d_diff", testName, PAGE_SUFFIX, i), resultImage);
                    hasDiff = true;
                }
            }
        }
        assertFalse(hasDiff);
    }

    private String generateLargeHtmlContent() {
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<html><head><title>Large Document</title></head><body>");
        for (int i = 0; i < 1000; i++) {
            htmlBuilder.append("<h1>Section ").append(i + 1).append("</h1>");
            htmlBuilder.append("<p>This is a paragraph in section ").append(i + 1).append(".</p>");
            htmlBuilder.append("<ul>");
            for (int j = 0; j < 10; j++) {
                htmlBuilder.append("<li>Item ").append(j + 1).append(" in section ").append(i + 1).append("</li>");
            }
            htmlBuilder.append("</ul>");
        }
        htmlBuilder.append("</body></html>");
        return htmlBuilder.toString();
    }

    protected String getCurrentMethodName() {
        return Thread.currentThread().getStackTrace()[2].getMethodName();
    }
}