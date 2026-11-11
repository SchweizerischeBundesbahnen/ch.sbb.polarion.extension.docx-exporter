package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import com.polarion.alm.tracker.model.IModule;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.columnWidths;
import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.documentXml;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Formulas in the cells of the tables of a Live Document; #455. With "Preserve table styles" a formula in a cell
 * came out as its TeX source, and a column of formulas got almost no width, so Word cut the formulas off. In the
 * PDF, a formula which ends a cell gets the space of the cell below it, and one which starts a cell hangs from the
 * top of its row, as in Word.
 */
@SkipTestWhenParamNotSet
@SuppressWarnings("ResultOfMethodCallIgnored")
class FormulasInTablesTest extends BaseDocxConverterTest {

    private static final Pattern CELL = Pattern.compile("<w:tc>(.*?)</w:tc>", Pattern.DOTALL);
    private static final Pattern TEXT = Pattern.compile("<w:t(?: [^>]*)?>([^<]*)</w:t>");

    @ParameterizedTest
    @CsvSource({
            "false, formulasInTables",
            "true, formulasInTablesPreserved",
    })
    @SneakyThrows
    void formulasInTables(boolean preserveTableStyles, String testName) {
        ExportParams params = ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation("PORTRAIT")
                .paperSize("A4")
                .preserveTableStyles(preserveTableStyles)
                .build();

        DocumentData<IModule> liveDoc = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title("Formulas in Tables Test")
                .content(readHtmlResource("formulasInTables"))
                .lastRevision("1")
                .revisionPlaceholder("1")
                .build();
        documentDataFactoryMockedStatic.when(() ->
                DocumentDataFactory.getDocumentData(eq(params), anyBoolean())).thenReturn(liveDoc);

        byte[] docx = converter.convertToDocx(params);
        assertNotNull(docx);
        writeReportDocx(testName + "_generated", docx);

        List<String> cells = cells(new String(documentXml(docx), StandardCharsets.UTF_8));
        // The formulas of "Expected formula:", of the column of formulas, the inline one and the fraction
        assertEquals(4, cells.stream().filter(cell -> cell.contains("<m:oMath>")).count(), "the cells with a Word equation");
        // A block formula has a paragraph of its own, after the text before it
        assertEquals(3, cells.stream().filter(cell -> cell.contains("<m:oMathPara>")).count(), "the cells with a block formula");
        String text = String.join(" | ", cells.stream().map(FormulasInTablesTest::text).toList());
        assertFalse(text.contains("\\frac") || text.contains("^{") || text.contains("CDATA"), "the TeX source is in the text of a table: " + text);

        // The numerator of the quadratic formula, which no line breaks, holds nine characters: more than 5 points each, 900 twips
        List<Long> formulas = columnWidths(docx).get(1).grid();
        assertTrue(formulas.get(1) >= 900, "the column of the formulas: " + formulas);
        assertTrue(formulas.get(1) > 3 * formulas.get(2), "the column of the formulas: " + formulas);

        File docxFile = getTestFile();
        try {
            Files.write(docxFile.toPath(), docx);
            compareContentUsingReferenceImages(testName, exportToPDF(docxFile));
        } finally {
            docxFile.delete();
        }
    }

    private static List<String> cells(String documentXml) {
        Matcher matcher = CELL.matcher(documentXml);
        return matcher.results().map(result -> result.group(1)).toList();
    }

    private static String text(String cell) {
        Matcher matcher = TEXT.matcher(cell);
        return String.join("", matcher.results().map(result -> result.group(1)).toList());
    }
}
