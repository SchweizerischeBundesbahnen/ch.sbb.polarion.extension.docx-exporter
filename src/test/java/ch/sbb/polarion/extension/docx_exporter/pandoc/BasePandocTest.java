package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.configuration.DocxExporterExtensionConfigurationExtension;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocInfo;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.util.MediaUtils;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.core.util.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SkipTestWhenParamNotSet
@ExtendWith({MockitoExtension.class, DocxExporterExtensionConfigurationExtension.class})
public abstract class BasePandocTest {

    public static final String IMPL_NAME_PARAM = "docxExporterImpl";

    protected static final String RESOURCE_BASE_PATH = "/pandoc/";
    protected static final String PANDOC_TEST_RESOURCES_FOLDER = RESOURCE_BASE_PATH + "html/";
    protected static final String PANDOC_TEST_TEMPLATE_RESOURCES_FOLDER = RESOURCE_BASE_PATH + "templates/";
    protected static final String PANDOC_TEST_PNG_RESOURCES_FOLDER = RESOURCE_BASE_PATH + "png/";
    protected static final String REPORTS_FOLDER_PATH = "target/surefire-reports/";
    public static final String PAGE_SUFFIX = "_page_";

    protected static final String EXT_HTML = ".html";
    protected static final String EXT_PNG = ".png";
    protected static final String EXT_DOCX = ".docx";
    protected static final String EXT_PDF = ".pdf";
    protected IModule module;

    /**
     * Returns the PandocServiceConnector using the shared container.
     */
    protected PandocServiceConnector getPandocServiceConnector() {
        assertTrue(SharedPandocContainer.getInstance().isRunning(), "Shared Pandoc container should be running");
        return new PandocServiceConnector(SharedPandocContainer.getBaseUrl());
    }

    public static String readHtmlResource(String resourceName) throws IOException {
        try (InputStream inputStream = BasePandocTest.class.getResourceAsStream(PANDOC_TEST_RESOURCES_FOLDER + resourceName + EXT_HTML)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourceName);
            }
            return StringUtils.readToString(inputStream);
        }
    }

    public static byte[] readTemplate(String resourceName) throws IOException {
        try (InputStream inputStream = BasePandocTest.class.getResourceAsStream(PANDOC_TEST_TEMPLATE_RESOURCES_FOLDER + resourceName + EXT_DOCX)) {
            if (inputStream == null) {
                throw new IOException("Template not found: " + resourceName);
            }
            return inputStream.readAllBytes();
        }
    }

    public static InputStream readPngResource(String resourceName) throws IOException {
        InputStream inputStream = BasePandocTest.class.getResourceAsStream(PANDOC_TEST_PNG_RESOURCES_FOLDER + resourceName + EXT_PNG);
        if (inputStream == null) {
            throw new IOException("PNG resource not found: " + resourceName);
        }
        return inputStream;
    }

    protected byte[] exportToDOCX(String html, byte[] template, @NotNull PandocParams params) {
        return getPandocServiceConnector().convertToDocx(html, template, params);
    }

    protected byte[] downloadTemplate() {
        return getPandocServiceConnector().getTemplate();
    }

    protected PandocInfo getPandocInfo() {
        return getPandocServiceConnector().getPandocInfo();
    }

    protected List<BufferedImage> getAllPagesAsImagesAndLogAsReports(@NotNull String fileName, byte[] pdfBytes) throws IOException {
        List<BufferedImage> result = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage image = MediaUtils.pdfPageToImage(doc, i);
                writeReportImage(String.format("%s%s%d", fileName, PAGE_SUFFIX, i), image); //write each page image to reports folder
                result.add(image);
            }
            return result;
        }
    }

    protected void writeReportImage(String resourceName, BufferedImage image) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(REPORTS_FOLDER_PATH + resourceName + EXT_PNG)) {
            fileOutputStream.write(MediaUtils.toPng(image));
        }
    }

    protected void writeReportDocx(String resourceName, byte[] data) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(REPORTS_FOLDER_PATH + resourceName + EXT_DOCX)) {
            fileOutputStream.write(data);
        }
    }

    protected byte[] exportToPDF(File docx) {
        return getPandocServiceConnector().convertToPDF(docx);
    }

    protected void writeReportPdf(String testName, String fileSuffix, byte[] bytes) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(getReportFilePath(testName, fileSuffix))) {
            fileOutputStream.write(bytes);
        }
    }

    protected String getReportFilePath(String testName, String fileSuffix) {
        return REPORTS_FOLDER_PATH + testName + "_" + fileSuffix + EXT_PDF;
    }

    protected void compareContentUsingReferenceImages(String testName, byte[] pdf) throws IOException {
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

    protected static @NotNull File getTestFile() throws IOException {
        Path tempPath = Files.createTempFile("test", ".docx");
        File newFile = tempPath.toFile();
        newFile.deleteOnExit();
        return newFile;
    }
}
