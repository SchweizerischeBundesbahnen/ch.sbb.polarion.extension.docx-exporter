package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import com.polarion.alm.tracker.model.IModule;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

/**
 * A test case as Polarion sends it: the bold header cells of its test steps table, and the
 * bold labels of its work item fields table. pandoc-service reads the style of a table cell
 * only with "Preserve table styles" on, so both settings are compared.
 * <p>
 * The rendered page does not show cell borders, so the gray borders of both tables are
 * checked in {@code word/document.xml}: a cell without one is drawn in the table's black default.
 */
@SkipTestWhenParamNotSet
@SuppressWarnings("ResultOfMethodCallIgnored")
class TableBoldStylesTest extends BaseDocxConverterTest {

    private static final Pattern CELL_PROPERTIES = Pattern.compile("<w:tcPr/>|<w:tcPr>.*?</w:tcPr>");
    private static final List<Pattern> GRAY_BORDER_SIDES = Stream.of("top", "left", "bottom", "right")
            .map(side -> Pattern.compile("<w:" + side + " [^>]*w:color=\"CCCCCC\""))
            .toList();
    private static final int TABLE_CELL_COUNT = 13; // 9 in the test steps table, 4 in the fields table

    @ParameterizedTest
    @CsvSource({
            "false, tableBoldStyles",
            "true, tableBoldStylesPreserved",
    })
    @SneakyThrows
    void tableBoldStyles(boolean preserveTableStyles, String testName) {
        ExportParams params = ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation("PORTRAIT")
                .paperSize("A4")
                .preserveTableStyles(preserveTableStyles)
                .build();

        DocumentData<IModule> liveDoc = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title("Table Bold Styles Test")
                .content(readHtmlResource("tableBoldStyles"))
                .lastRevision("1")
                .revisionPlaceholder("1")
                .build();
        documentDataFactoryMockedStatic.when(() ->
                DocumentDataFactory.getDocumentData(eq(params), anyBoolean())).thenReturn(liveDoc);

        byte[] docx = converter.convertToDocx(params);
        assertNotNull(docx);
        writeReportDocx(testName + "_generated", docx);
        if (preserveTableStyles) {
            List<String> cells = cellProperties(docx);
            assertEquals(TABLE_CELL_COUNT, cells.size());
            assertEquals(List.of(), cells.stream().filter(cell -> !hasGrayBorderOnEverySide(cell)).toList());
        }

        File docxFile = getTestFile();
        try {
            Files.write(docxFile.toPath(), docx);
            compareContentUsingReferenceImages(testName, exportToPDF(docxFile));
        } finally {
            docxFile.delete();
        }
    }

    private static List<String> cellProperties(byte[] docx) throws IOException {
        List<String> cells = new ArrayList<>();
        Matcher matcher = CELL_PROPERTIES.matcher(readDocumentXml(docx));
        while (matcher.find()) {
            cells.add(matcher.group());
        }
        return cells;
    }

    private static boolean hasGrayBorderOnEverySide(String cell) {
        return GRAY_BORDER_SIDES.stream().allMatch(side -> side.matcher(cell).find());
    }

    private static String readDocumentXml(byte[] docx) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("word/document.xml not found");
    }
}
