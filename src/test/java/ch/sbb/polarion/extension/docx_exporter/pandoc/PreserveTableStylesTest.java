package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the {@code preserve_table_styles} parameter causes pandoc-service
 * to retain CSS table cell styles (background-color, borders) as native DOCX shading
 * in {@code word/document.xml}.
 */
@SkipTestWhenParamNotSet
class PreserveTableStylesTest extends BasePandocTest {

    private static final String TABLE_HTML = """
            <html><head><title>Table Styles Test</title></head><body>
            <h1>Table with CSS styles</h1>
            <table border="1">
                <tr>
                    <th style="background-color: #4472C4; color: white; border: 2px solid #2F5496;">Header 1</th>
                    <th style="background-color: #4472C4; color: white; border: 2px solid #2F5496;">Header 2</th>
                </tr>
                <tr>
                    <td style="background-color: #D9E2F3; border: 1px solid #8EAADB;">Cell A1</td>
                    <td style="background-color: #D9E2F3; border: 1px solid #8EAADB;">Cell A2</td>
                </tr>
                <tr>
                    <td style="background-color: #FFFFFF; border: 1px solid #8EAADB;">Cell B1</td>
                    <td style="background-color: #FFFFFF; border: 1px solid #8EAADB;">Cell B2</td>
                </tr>
            </table>
            </body></html>
            """;

    @Test
    void tableStylesPreservedWhenEnabled() throws Exception {
        byte[] docBytes = exportToDOCX(TABLE_HTML, readTemplate("reference_template"),
                PandocParams.builder().preserveTableStyles(true).build());
        assertNotNull(docBytes);
        writeReportDocx("tableStylesPreservedWhenEnabled_generated", docBytes);

        assertTableShadingPresent(docBytes);
    }

    @Test
    void tableStylesNotPreservedWhenDisabled() throws Exception {
        byte[] docBytes = exportToDOCX(TABLE_HTML, readTemplate("reference_template"),
                PandocParams.builder().build());
        assertNotNull(docBytes);
        writeReportDocx("tableStylesNotPreservedWhenDisabled_generated", docBytes);

        assertTableShadingAbsent(docBytes);
    }

    private void assertTableShadingPresent(byte[] docBytes) throws IOException {
        String documentXml = extractDocumentXml(docBytes);
        assertTrue(documentXml.contains("<w:shd"), "document.xml should contain <w:shd> (cell shading) when preserveTableStyles is enabled");
    }

    private void assertTableShadingAbsent(byte[] docBytes) throws IOException {
        String documentXml = extractDocumentXml(docBytes);
        assertFalse(documentXml.contains("<w:shd"), "document.xml should not contain <w:shd> (cell shading) when preserveTableStyles is disabled");
    }

    private String extractDocumentXml(byte[] docBytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
            }
        }
        byte[] documentXmlBytes = entries.get("word/document.xml");
        assertNotNull(documentXmlBytes, "word/document.xml should be present in the DOCX");
        return new String(documentXmlBytes, StandardCharsets.UTF_8);
    }
}
