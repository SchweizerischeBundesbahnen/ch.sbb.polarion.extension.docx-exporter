package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.util.MediaUtils;
import com.google.gwt.thirdparty.guava.common.io.Files;
import lombok.SneakyThrows;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.docx4j.Docx4J;
import org.docx4j.convert.out.FOSettings;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SkipTestWhenParamNotSet
class BasicTest extends BasePandocTest {

    @Test
    void testInvalidTemplate() {
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
        newFile.setReadable(false, false);
        newFile.setReadable(true, true);
        newFile.setWritable(false, false);
        newFile.setWritable(true, true);
        newFile.setExecutable(false, false);
        newFile.deleteOnExit();
        try {
            Files.write(doc, newFile);
            byte[] pdf = exportToPDF(newFile);
            assertNotNull(pdf);
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
        newFile.setReadable(false, false);
        newFile.setReadable(true, true);
        newFile.setWritable(false, false);
        newFile.setWritable(true, true);
        newFile.setExecutable(false, false);
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

    @Test
    @SneakyThrows
    void shouldConsiderPageBreaks() {
        String html = readHtmlResource("testPageBreak");
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"));
        Files.write(docBytes, new File("target/testPageBreak.docx"));
        assertEquals(3, getPageCount(docBytes));
    }

    private int getPageCount(byte[] docBytes) {
        try {
            try (PDDocument document = Loader.loadPDF(exportToPDF(docBytes))) {
                return document.getNumberOfPages();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private byte[] exportToPDF(byte[] docBytes) throws Docx4JException {
        WordprocessingMLPackage doc = WordprocessingMLPackage.load(new ByteArrayInputStream(docBytes));
        FOSettings foSettings = new FOSettings(doc);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Docx4J.toFO(foSettings, baos, Docx4J.FLAG_EXPORT_PREFER_XSL);
        return baos.toByteArray();
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
        for (int i = 0; i < 500; i++) {
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
