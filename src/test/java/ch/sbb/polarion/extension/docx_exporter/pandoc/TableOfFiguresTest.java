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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

@SkipTestWhenParamNotSet
@SuppressWarnings("ResultOfMethodCallIgnored")
class TableOfFiguresTest extends BaseDocxConverterTest {

    private static final Pattern PAGE_REF_TARGET = Pattern.compile("PAGEREF\\s+(\\S+)");
    private static final Pattern BOOKMARK_NAME = Pattern.compile("w:name=\"(_Toc[^\"]*)\"");
    private static final Pattern HYPERLINK_BLOCK = Pattern.compile("<w:hyperlink\\b(.*?)</w:hyperlink>", Pattern.DOTALL);
    private static final String TOF_FIELD_CODE = "TOC \\h \\z \\f F";
    private static final String TOT_FIELD_CODE = "TOC \\h \\z \\f T";
    private static final String TOC_ENTRY_STYLE = "TOC1";

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
                ),
                Arguments.of(
                        // A figure caption directly followed by the work item's fields-at-end table
                        // must stay in the Table of Figures (docx-exporter#397)
                        "figureCaptionFollowedByTable",
                        "Figure Caption Followed by Table Test",
                        List.of("Figure 1 -- First Picture", "Figure 2 -- Second Picture"),
                        List.of("Table 1 -- First Table", "Table 2 -- Second Table"),
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

            // Each caption's own TC field carries the list flag: \\f F collects it into the Table of
            // Figures, \\f T into the Table of Tables
            for (String expected : expectedFigures) {
                assertEquals("F", findTcFlag(documentXml, expected),
                        "TC entry of figure '" + expected + "' is expected to carry \\f F");
            }
            for (String expected : expectedTables) {
                assertEquals("T", findTcFlag(documentXml, expected),
                        "TC entry of table '" + expected + "' is expected to carry \\f T");
            }

            // The pre-filled entries sit in the list they belong to
            Map<String, List<String>> listEntries = collectPrefilledEntries(document);
            assertEntriesMatch(listEntries.get(TOF_FIELD_CODE), expectedFigures, "Table of Figures");
            assertEntriesMatch(listEntries.get(TOT_FIELD_CODE), expectedTables, "Table of Tables");

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
            List<String> pageRefTargets = findAll(documentXml, PAGE_REF_TARGET);
            assertEquals(expectedEntries, pageRefTargets.size(),
                    "Expected one pre-filled PAGEREF entry per caption");

            // Targets are matched against the bookmarks, not merely counted: a PAGEREF pointing at
            // a bookmark this document never defines resolves to "Error! Bookmark not defined."
            Set<String> bookmarks = new HashSet<>(findAll(documentXml, BOOKMARK_NAME));
            for (String target : pageRefTargets) {
                assertTrue(bookmarks.contains(target),
                        "PAGEREF points at '" + target + "', which no bookmark in this document defines");
            }

            assertEquals(expectedEntries, countHyperlinkedPageRefs(documentXml),
                    "Every pre-filled entry is expected to sit inside its own hyperlink");
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

    /**
     * Counts hyperlinks that wrap a PAGEREF field. Counting the two separately would let an
     * unrelated hyperlink stand in for a missing one.
     */
    private int countHyperlinkedPageRefs(String documentXml) {
        int count = 0;
        Matcher matcher = HYPERLINK_BLOCK.matcher(documentXml);
        while (matcher.find()) {
            if (matcher.group(1).contains("PAGEREF")) {
                count++;
            }
        }
        return count;
    }

    private List<String> findAll(String documentXml, Pattern pattern) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(documentXml);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
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
     * Returns the list flag (F or T) of the TC field that belongs to the given caption.
     * The field is split into runs: {@code TC "}, the caption text, then {@code " \f F \l "1"}.
     * The caption text may continue after the given prefix, e.g. "Figure 1" matches "Figure 1 -- Diagram".
     */
    private String findTcFlag(String documentXml, String caption) {
        Pattern tcField = Pattern.compile("TC \"</w:instrText>.*?<w:instrText[^>]*>" + Pattern.quote(caption)
                + "[^<]*</w:instrText>.*?<w:instrText[^>]*>\" \\\\f ([FT])", Pattern.DOTALL);
        Matcher matcher = tcField.matcher(documentXml);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Collects the text of the pre-filled entries per list, keyed by the list's field code.
     * An entry is a TOC1 paragraph. The first one also holds the field code.
     */
    private Map<String, List<String>> collectPrefilledEntries(XWPFDocument document) {
        Map<String, List<String>> entries = new HashMap<>();
        List<String> current = null;
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String paragraphXml = paragraph.getCTP().xmlText();
            for (String fieldCode : List.of(TOF_FIELD_CODE, TOT_FIELD_CODE)) {
                if (paragraphXml.contains(fieldCode)) {
                    current = entries.computeIfAbsent(fieldCode, key -> new ArrayList<>());
                }
            }
            if (!TOC_ENTRY_STYLE.equals(paragraph.getStyle())) {
                current = null;
            } else if (current != null) {
                current.add(paragraph.getText());
            }
        }
        return entries;
    }

    private void assertEntriesMatch(List<String> entries, List<String> expectedCaptions, String listName) {
        List<String> actual = entries == null ? List.of() : entries;
        assertEquals(expectedCaptions.size(), actual.size(), listName + " entries: " + actual);
        for (int i = 0; i < expectedCaptions.size(); i++) {
            assertTrue(actual.get(i).startsWith(expectedCaptions.get(i)),
                    listName + " entry " + (i + 1) + " is expected to be '" + expectedCaptions.get(i) + "', found '" + actual.get(i) + "'");
        }
    }
}
