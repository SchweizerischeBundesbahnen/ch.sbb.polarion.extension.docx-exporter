package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.DocumentData;
import ch.sbb.polarion.extension.docx_exporter.rest.model.documents.id.LiveDocId;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentDataFactory;
import com.polarion.alm.tracker.model.IModule;
import lombok.SneakyThrows;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

@SkipTestWhenParamNotSet
@SuppressWarnings("ResultOfMethodCallIgnored")
class TableOfFiguresTest extends BaseDocxConverterTest {

    private static Stream<Arguments> provideTableOfFiguresTestCases() {
        return Stream.of(
                Arguments.of(
                        "tableOfFigures",
                        "Table of Figures Test",
                        List.of("Figure 1 -- Component Diagram",
                                "Figure 2 -- Network Architecture",
                                "Figure 3 -- Security Layers"),
                        List.of(),
                        true,   // expectTofField
                        false   // expectTotField
                ),
                Arguments.of(
                        "tableOfTables",
                        "Table of Tables Test",
                        List.of(),
                        List.of("Table 1 -- Minimum Hardware Requirements",
                                "Table 2 -- Required Software Versions",
                                "Table 3 -- Environment Variables"),
                        false,  // expectTofField
                        true    // expectTotField
                ),
                Arguments.of(
                        "tableOfFiguresAndTables",
                        "Combined ToF and ToT Test",
                        List.of("Figure 1", "Figure 2"),
                        List.of("Table 1", "Table 2"),
                        true,   // expectTofField
                        true    // expectTotField
                )
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("provideTableOfFiguresTestCases")
    @SneakyThrows
    void testTableOfFiguresGeneration(String htmlResource, String title,
                                      List<String> expectedFigures,
                                      List<String> expectedTables,
                                      boolean expectTofField,
                                      boolean expectTotField) {
        ExportParams params = ExportParams.builder()
                .projectId("test")
                .locationPath("testLocation")
                .orientation("PORTRAIT")
                .paperSize("A4")
                .build();

        DocumentData<IModule> liveDoc = DocumentData.creator(module)
                .id(LiveDocId.from("testProjectId", "_default", "testDocumentId"))
                .title(title)
                .content(readHtmlResource(htmlResource))
                .lastRevision("1")
                .revisionPlaceholder("1")
                .build();
        documentDataFactoryMockedStatic.when(() ->
                DocumentDataFactory.getDocumentData(eq(params), anyBoolean())).thenReturn(liveDoc);

        byte[] doc = converter.convertToDocx(params);
        assertNotNull(doc);

        writeReportDocx(htmlResource, doc);

        verifyDocxStructure(doc, expectedFigures, expectedTables, expectTofField, expectTotField);
    }

    private void verifyDocxStructure(byte[] docBytes,
                                     List<String> expectedFigures,
                                     List<String> expectedTables,
                                     boolean expectTofField,
                                     boolean expectTotField) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docBytes))) {
            String fullText = extractFullText(document);
            String documentXml = getDocumentXml(document);

            // Verify expected figure captions exist in document
            for (String expected : expectedFigures) {
                assertTrue(fullText.contains(expected),
                        "Expected figure caption not found: '" + expected + "'");
            }

            // Verify expected table captions exist in document
            for (String expected : expectedTables) {
                assertTrue(fullText.contains(expected),
                        "Expected table caption not found: '" + expected + "'");
            }

            // Verify TOF field exists (TOC with \f F switch)
            if (expectTofField) {
                assertTrue(hasTocFieldWithSwitch(documentXml, "F"),
                        "Table of Figures field (TOC \\f F) not found in document");
            }

            // Verify TOT field exists (TOC with \f T switch)
            if (expectTotField) {
                assertTrue(hasTocFieldWithSwitch(documentXml, "T"),
                        "Table of Tables field (TOC \\f T) not found in document");
            }

            // Verify TC entries exist for figures
            for (String expected : expectedFigures) {
                assertTrue(hasTcEntry(documentXml, expected, "F"),
                        "TC entry not found for figure: '" + expected + "'");
            }

            // Verify TC entries exist for tables
            for (String expected : expectedTables) {
                assertTrue(hasTcEntry(documentXml, expected, "T"),
                        "TC entry not found for table: '" + expected + "'");
            }
        }
    }

    private String extractFullText(XWPFDocument document) {
        StringBuilder text = new StringBuilder();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            text.append(paragraph.getText()).append("\n");
        }
        return text.toString();
    }

    private String getDocumentXml(XWPFDocument document) {
        StringBuilder xml = new StringBuilder();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            xml.append(paragraph.getCTP().xmlText());
        }
        return xml.toString();
    }

    /**
     * Check if document contains a TOC field with specific switch (F for figures, T for tables)
     */
    private boolean hasTocFieldWithSwitch(String documentXml, String switchIdentifier) {
        // Looking for: TOC \h \z \f F  or  TOC \h \z \f T
        return documentXml.contains("TOC") && documentXml.contains("\\f " + switchIdentifier);
    }

    /**
     * Check if document contains a TC (Table of Contents entry) field for the given caption
     */
    private boolean hasTcEntry(String documentXml, String caption, String switchIdentifier) {
        // Looking for: TC "Figure 1 -- Component Diagram" \f F \l "1"
        // We check if TC entry contains both the caption text and the switch
        return documentXml.contains("TC") &&
                documentXml.contains(caption) &&
                documentXml.contains("\\f " + switchIdentifier);
    }
}
