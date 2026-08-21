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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                        false,  // expectTotField
                        "Figure",
                        null
                ),
                Arguments.of(
                        "tableOfTables",
                        "Table of Tables Test",
                        List.of(),
                        List.of("Table 1 -- Minimum Hardware Requirements",
                                "Table 2 -- Required Software Versions",
                                "Table 3 -- Environment Variables"),
                        false,  // expectTofField
                        true,   // expectTotField
                        null,
                        "Table"
                ),
                Arguments.of(
                        "tableOfTablesLocalized",
                        "Localized Table of Tables Test",
                        List.of(),
                        List.of("Tabelle 1 -- Mindestanforderungen an die Hardware",
                                "Tabelle 2 -- Erforderliche Softwareversionen",
                                "Tabelle 3 -- Umgebungsvariablen"),
                        false,  // expectTofField
                        true,   // expectTotField
                        null,
                        "Tabelle"
                ),
                Arguments.of(
                        "tableOfFiguresAndTables",
                        "Combined ToF and ToT Test",
                        List.of("Figure 1", "Figure 2"),
                        List.of("Table 1", "Table 2"),
                        true,   // expectTofField
                        true,   // expectTotField
                        "Figure",
                        "Table"
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
                                      boolean expectTotField,
                                      String figureSequence,
                                      String tableSequence) {
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

        verifyDocxStructure(doc, expectedFigures, expectedTables, expectTofField, expectTotField, figureSequence, tableSequence);
    }

    private void verifyDocxStructure(byte[] docBytes,
                                     List<String> expectedFigures,
                                     List<String> expectedTables,
                                     boolean expectTofField,
                                     boolean expectTotField,
                                     String figureSequence,
                                     String tableSequence) throws IOException {
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

            // Each caption number is a SEQ field, not plain text, so Word renumbers on update.
            // The sequence name is Polarion's own (`data-sequence`), which may be localized.
            if (figureSequence != null) {
                assertEquals(expectedFigures.size(), countSeqFields(documentXml, figureSequence),
                        "Expected one 'SEQ " + figureSequence + "' field per figure caption");
            }
            if (tableSequence != null) {
                assertEquals(expectedTables.size(), countSeqFields(documentXml, tableSequence),
                        "Expected one 'SEQ " + tableSequence + "' field per table caption");
            }

            // The ToF/ToT arrive pre-filled: one hyperlinked PAGEREF entry per caption, each
            // pointing at a bookmark the same document defines. Without them the tables render
            // empty until the reader presses F9.
            int expectedEntries = expectedFigures.size() + expectedTables.size();
            assertEquals(expectedEntries, countOccurrences(documentXml, "PAGEREF _Toc"),
                    "Expected one pre-filled PAGEREF entry per caption");
            assertTrue(countOccurrences(documentXml, "<w:hyperlink") >= expectedEntries,
                    "Pre-filled entries are expected to be hyperlinks");
            assertTrue(countOccurrences(documentXml, "w:name=\"_Toc") >= expectedEntries,
                    "Every PAGEREF entry needs a bookmark to point at");
        }
    }

    /**
     * Counts SEQ fields of one sequence, e.g. {@code SEQ Figure \* ARABIC} - the field Word uses to
     * number captions. Polarion's sequence name is carried over as-is, so a localized document
     * yields e.g. {@code SEQ Tabelle}.
     */
    private int countSeqFields(String documentXml, String sequenceName) {
        return countOccurrences(documentXml, "SEQ " + sequenceName + " \\* ARABIC");
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    private String extractFullText(XWPFDocument document) {
        StringBuilder text = new StringBuilder();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            text.append(paragraph.getText()).append("\n");
        }
        return text.toString();
    }

    private String getDocumentXml(XWPFDocument document) {
        // The whole body, not just top-level paragraphs: caption and entry runs also live inside
        // tables, and the pre-filled ToF/ToT entries are what this test now asserts on.
        return document.getDocument().getBody().xmlText();
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
