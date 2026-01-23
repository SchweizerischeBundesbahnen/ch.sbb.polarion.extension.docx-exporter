package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocInfo;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import jakarta.xml.bind.JAXBElement;
import lombok.SneakyThrows;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.docx4j.Docx4J;
import org.docx4j.convert.out.FOSettings;
import org.docx4j.fonts.PhysicalFonts;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.SectPr;
import org.docx4j.wml.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SkipTestWhenParamNotSet
@SuppressWarnings("ResultOfMethodCallIgnored")
class BasicTest extends BasePandocTest {

    static {
        // Workaround for docx4j font discovery issue with some OpenType fonts.
        // Must be set BEFORE IdentityPlusMapper class is loaded (which triggers PhysicalFonts.discoverPhysicalFonts()).
        // If this is not set early enough, docx4j may discover problematic OpenType fonts,
        // which can cause exceptions or incorrect font rendering in generated documents, leading to test failures.
        // Regex limits font discovery to common fonts, avoiding problematic OpenType fonts.
        PhysicalFonts.setRegex(".*(Courier New|Arial|Times New Roman|Georgia|Trebuchet|Verdana|Helvetica).*");
    }

    @Test
    void testInvalidTemplate() {
        Exception exception = null;
        try {
            exportToDOCX(readHtmlResource(getCurrentMethodName()), readTemplate("invalid_template"), PandocParams.builder().build());
        } catch (Exception e) {
            exception = e;
        }
        assertNotNull(exception, "Expected an exception for invalid template");
    }

    @Test
    void testLargeDocument() throws Exception {
        String largeHtml = generateLargeHtmlContent();
        byte[] doc = exportToDOCX(largeHtml, readTemplate("reference_template"), PandocParams.builder().build());
        writeReportDocx("testLargeDocument_generated", doc);
        assertNotNull(doc);
        File newFile = getTestFile();
        newFile.deleteOnExit();
        try {
            Files.write(newFile.toPath(), doc);
            byte[] pdf = exportToPDF(newFile);
            assertNotNull(pdf);
        } finally {
            newFile.delete();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"testSimple", "testTable", "testLists", "testParagraphs"})
    void runTest(String testName) throws Exception {
        byte[] doc = exportToDOCX(readHtmlResource(testName), readTemplate("reference_template"), PandocParams.builder().build());
        assertNotNull(doc);
        writeReportDocx("testName_generated", doc);
        File newFile = getTestFile();
        try {
            Files.write(newFile.toPath(), doc);
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
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().build());
        writeReportDocx("shouldConsiderPageBreaks_generated", docBytes);
        assertEquals(3, getPageCount(docBytes));
    }


    @Test
    @SneakyThrows
    void testOrientationAndPaperSize() {
        String html = readHtmlResource("testSimple");
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().orientation("landscape").paperSize("B5").build());
        writeReportDocx("testOrientationAndPaperSize_B5_landscape_generated", docBytes);
        assertTrue(hasDimensions(docBytes, 14144, 9979));

        docBytes = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().paperSize("A3").build());
        writeReportDocx("testOrientationAndPaperSize_A3_portrait_generated", docBytes);
        assertTrue(hasDimensions(docBytes, 16838, 23811));
    }

    @Test
    @SneakyThrows
    void testPageOrientation() {
        String html = readHtmlResource("testPageOrientation");
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().orientation("landscape").paperSize("B5").build());
        writeReportDocx("testPageOrientation_generated", docBytes);

        docBytes = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().paperSize("A4").build());
        assertTrue(hasOrientationOnPage(docBytes, 1, "landscape"));
    }

    @Test
    @SneakyThrows
    void testPageOrientationChanges() {
        String html = readHtmlResource("testPageOrientation");
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().build());
        writeReportDocx("testPageOrientationChanges_generated", docBytes);

        // First page: portrait (default)
        assertTrue(hasOrientationOnPage(docBytes, 0, "portrait"));

        // Second page: landscape (after \pageLandscape)
        assertTrue(hasOrientationOnPage(docBytes, 1, "landscape"));

        // Third page: portrait (after \pagePortrait)
        assertTrue(hasOrientationOnPage(docBytes, 2, "portrait"));
    }

    @Test
    @SneakyThrows
    void testMultipleOrientationSwitches() {
        String html = readHtmlResource("testPageOrientation");
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().build());
        writeReportDocx("testMultipleOrientationSwitches_generated", docBytes);

        // Verify section count matches orientation changes
        int sectionCount = getPageSectionCount(docBytes);
        assertTrue(sectionCount >= 3, "Expected at least 3 sections for orientation changes");
    }

    @Test
    @SneakyThrows
    void testLandscapeOrientationWithTableContent() {
        String html = readHtmlResource("testPageOrientation");
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().build());
        writeReportDocx("testLandscapeOrientationWithTable_generated", docBytes);

        // Second page should be landscape and contain table
        assertTrue(hasOrientationOnPage(docBytes, 1, "landscape"));
        assertTrue(pageContainsTable(docBytes, 1));
    }

    @Test
    @SneakyThrows
    void testStartingOrientationPreserved() {
        String html = readHtmlResource("testPageOrientation");
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().orientation("landscape").build());
        writeReportDocx("testStartingLandscapeOrientation_generated", docBytes);

        // First page should start in landscape
        assertTrue(hasOrientationOnPage(docBytes, 0, "landscape"));
    }

    @Test
    @SneakyThrows
    void testOrientationWithA4PaperSize() {
        String html = readHtmlResource("testPageOrientation");
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"),
                PandocParams.builder().paperSize("A4").build());
        writeReportDocx("testOrientationA4_generated", docBytes);

        assertTrue(hasDimensions(docBytes, 11906, 16838)); // A4 portrait dimensions
        assertTrue(hasOrientationOnPage(docBytes, 0, "portrait"));
    }

    @Test
    @SneakyThrows
    void testConsecutiveLandscapePages() {
        String html = "<p>Page 1 landscape</p><p>\\pageLandscape</p>" +
                "<p>Page 2 landscape</p><p>\\pageLandscape</p>" +
                "<p>Page 3 portrait</p><p>\\pagePortrait</p>";
        byte[] docBytes = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().build());
        writeReportDocx("testConsecutiveLandscapePages_generated", docBytes);

        assertTrue(hasOrientationOnPage(docBytes, 0, "landscape"));
        assertTrue(hasOrientationOnPage(docBytes, 1, "landscape"));
        assertTrue(hasOrientationOnPage(docBytes, 2, "portrait"));
    }

    private int getPageSectionCount(byte[] docBytes) throws Exception {
        WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docBytes));
        MainDocumentPart documentPart = wordMLPackage.getMainDocumentPart();
        List<Object> sectPrNodes = documentPart.getJAXBNodesViaXPath("//w:sectPr", true);
        return sectPrNodes.size();
    }

    private boolean pageContainsTable(byte[] docBytes, int pageIndex) throws Exception {
        WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docBytes));
        MainDocumentPart documentPart = wordMLPackage.getMainDocumentPart();
        // Get all section nodes to identify page boundaries
        List<Object> sectPrNodes = documentPart.getJAXBNodesViaXPath("//w:sectPr", true);
        if (sectPrNodes.isEmpty() || pageIndex < 0 || pageIndex >= sectPrNodes.size()) {
            return false;
        }
        // Get all content elements
        List<Object> contentElements = documentPart.getContent();
        // Group content by section
        int currentSection = 1;
        boolean foundTable = false;
        for (Object element : contentElements) {
            // Check if this element is a section break
            if (element instanceof JAXBElement) {
                Object value = ((JAXBElement<?>) element).getValue();
                if (value instanceof SectPr) {
                    currentSection++;
                    continue;
                }
                // Check for table in the correct section
                if (currentSection == pageIndex && value instanceof org.docx4j.wml.Tbl) {
                    foundTable = true;
                    break;
                }
            } else {
                // Check for table in the correct section
                if (currentSection == pageIndex && element instanceof org.docx4j.wml.Tbl) {
                    foundTable = true;
                    break;
                }
            }
        }
        return foundTable;
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

    @Test
    @SneakyThrows
    void shouldDownloadTemplate() {
        byte[] docBytes = downloadTemplate();
        assertTrue(docBytes.length > 0);
    }

    @Test
    @SneakyThrows
    void shouldGetPandocInfo() {
        PandocInfo pandocInfo = getPandocInfo();
        assertNotNull(pandocInfo.getPandoc());
        assertNotNull(pandocInfo.getPandocService());
        assertNotNull(pandocInfo.getPython());
        assertNotNull(pandocInfo.getTimestamp());
    }

    private byte[] exportToPDF(byte[] docBytes) throws Docx4JException {
        WordprocessingMLPackage doc = WordprocessingMLPackage.load(new ByteArrayInputStream(docBytes));
        FOSettings foSettings = new FOSettings(doc);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Docx4J.toFO(foSettings, baos, Docx4J.FLAG_EXPORT_PREFER_XSL);
        return baos.toByteArray();
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

    private boolean containsText(byte[] docBytes, String search) {
        if (search == null || search.isEmpty()) {
            return false;
        }

        try {
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docBytes));
            MainDocumentPart documentPart = wordMLPackage.getMainDocumentPart();
            List<Object> textNodes = documentPart.getJAXBNodesViaXPath("//w:t", true);

            for (Object node : textNodes) {
                if (node instanceof JAXBElement<?> jaxbElement) {
                    Object value = jaxbElement.getValue();
                    if (value instanceof Text textElement) {
                        String textContent = textElement.getValue();
                        if (textContent != null && textContent.contains(search)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasDimensions(byte[] docBytes, int width, int height) {
        try {
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docBytes));
            MainDocumentPart documentPart = wordMLPackage.getMainDocumentPart();

            // Get section properties from the document
            List<Object> sectPrNodes = documentPart.getJAXBNodesViaXPath("//w:sectPr", true);

            return !sectPrNodes.isEmpty() && sectPrNodes.get(0) instanceof SectPr sectPr && sectPr.getPgSz() != null &&
                    sectPr.getPgSz().getW().intValue() == width && sectPr.getPgSz().getH().intValue() == height;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasOrientationOnPage(byte[] docBytes, int pageIndex, String expectedOrientation) {
        try {
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docBytes));
            MainDocumentPart documentPart = wordMLPackage.getMainDocumentPart();

            // Get all section properties
            List<Object> sectPrNodes = documentPart.getJAXBNodesViaXPath("//w:sectPr", true);

            if (sectPrNodes.isEmpty() || pageIndex >= sectPrNodes.size()) {
                return false;
            }

            SectPr sectPr = (SectPr) sectPrNodes.get(pageIndex);
            if (sectPr.getPgSz() == null) {
                return false;
            }

            boolean isLandscape = sectPr.getPgSz().getOrient() != null &&
                    "landscape".equalsIgnoreCase(sectPr.getPgSz().getOrient().toString());
            boolean expectedLandscape = "landscape".equalsIgnoreCase(expectedOrientation);

            return isLandscape == expectedLandscape;
        } catch (Exception e) {
            return false;
        }
    }

    protected String getCurrentMethodName() {
        return Thread.currentThread().getStackTrace()[2].getMethodName();
    }
}
