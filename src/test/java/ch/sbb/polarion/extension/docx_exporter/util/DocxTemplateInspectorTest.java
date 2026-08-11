package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.model.TemplateDetails;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DocxTemplateInspectorTest {

    private static final String STYLES_ENTRY = "word/styles.xml";
    private static final String CORE_PROPERTIES_ENTRY = "docProps/core.xml";

    private static final String STYLES_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:docDefaults><w:rPrDefault><w:rPr/></w:rPrDefault></w:docDefaults>
              <w:latentStyles><w:lsdException w:name="Normal"/></w:latentStyles>
              <w:style w:type="paragraph" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
              <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/></w:style>
              <w:style w:type="character" w:styleId="Hyperlink"><w:name w:val="Hyperlink"/></w:style>
            </w:styles>
            """;

    private static final String CORE_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                               xmlns:dcterms="http://purl.org/dc/terms/">
              <dcterms:created>2024-01-02T09:15:00Z</dcterms:created>
              <dcterms:modified>2024-06-13T08:45:12Z</dcterms:modified>
            </cp:coreProperties>
            """;

    private final DocxTemplateInspector inspector = new DocxTemplateInspector();

    @Test
    void readsStyleCountAndModificationDate() {
        TemplateDetails details = inspector.inspect(docx(Map.of(STYLES_ENTRY, STYLES_XML, CORE_PROPERTIES_ENTRY, CORE_XML)));

        assertEquals(3, details.getStyleCount());
        assertEquals("2024-06-13 08:45:12", details.getModifiedDate());
    }

    @Test
    void reportsNoModificationDateWhenTheDocumentCarriesNoCoreProperties() {
        TemplateDetails details = inspector.inspect(docx(Map.of(STYLES_ENTRY, STYLES_XML)));

        assertEquals(3, details.getStyleCount());
        assertNull(details.getModifiedDate());
    }

    @Test
    void reportsNoModificationDateWhenTheCorePropertiesCarryNoTimestamp() {
        String createdOnly = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                                   xmlns:dcterms="http://purl.org/dc/terms/">
                  <dcterms:created>2024-01-02T09:15:00Z</dcterms:created>
                </cp:coreProperties>
                """;

        assertNull(inspector.inspect(docx(Map.of(STYLES_ENTRY, STYLES_XML, CORE_PROPERTIES_ENTRY, createdOnly))).getModifiedDate());
    }

    @Test
    void reportsNoModificationDateWhenTheTimestampIsBlank() {
        String blankModified = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                                   xmlns:dcterms="http://purl.org/dc/terms/">
                  <dcterms:modified>   </dcterms:modified>
                </cp:coreProperties>
                """;

        assertNull(inspector.inspect(docx(Map.of(STYLES_ENTRY, STYLES_XML, CORE_PROPERTIES_ENTRY, blankModified))).getModifiedDate());
    }

    @Test
    void countsStylesWhateverPrefixTheNamespaceIsBoundTo() {
        String reboundStyles = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styles xmlns="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <style styleId="Normal"/>
                  <style styleId="Heading1"/>
                </styles>
                """;

        assertEquals(2, inspector.inspect(docx(Map.of(STYLES_ENTRY, reboundStyles))).getStyleCount());
    }

    @Test
    void rejectsAFileThatIsNotAZipArchive() {
        byte[] notAZip = "this is a plain text file".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(notAZip));
    }

    @Test
    void rejectsAZipArchiveThatIsNotAWordDocument() {
        byte[] zipWithoutStyles = docx(Map.of("word/document.xml", "<document/>"));

        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(zipWithoutStyles));
    }

    @Test
    void rejectsADocumentWhoseStylesAreUnreadable() {
        byte[] brokenStyles = docx(Map.of(STYLES_ENTRY, "<w:styles><w:style></w:styles>"));

        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(brokenStyles));
    }

    @Test
    void rejectsADocumentWhoseCorePropertiesAreUnreadable() {
        byte[] brokenCoreProperties = docx(Map.of(STYLES_ENTRY, STYLES_XML, CORE_PROPERTIES_ENTRY, "<cp:coreProperties><dcterms:modified>"));

        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(brokenCoreProperties));
    }

    @Test
    void rejectsAnArchiveThatBreaksOffMidEntry() {
        byte[] archive = docx(Map.of(STYLES_ENTRY, STYLES_XML.repeat(500)));
        byte[] truncated = Arrays.copyOf(archive, archive.length / 2);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> inspector.inspect(truncated));
        assertInstanceOf(IOException.class, thrown.getCause());
    }

    @Test
    void rejectsAnEntryThatInflatesBeyondTheSizeCap() {
        byte[] zipBomb = docxWithOversizedStylesEntry();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> inspector.inspect(zipBomb));
        assertInstanceOf(IOException.class, thrown.getCause());
    }

    @Test
    void rejectsADocumentWhoseStylesCannotBeOpened() throws XMLStreamException {
        XMLInputFactory factory = mock(XMLInputFactory.class);
        when(factory.createXMLStreamReader(any(InputStream.class))).thenThrow(new XMLStreamException("Cannot open the styles"));
        byte[] archive = docx(Map.of(STYLES_ENTRY, STYLES_XML));

        try (MockedStatic<XMLInputFactory> factories = mockStatic(XMLInputFactory.class)) {
            factories.when(XMLInputFactory::newFactory).thenReturn(factory);

            assertThrows(IllegalArgumentException.class, () -> inspector.inspect(archive));
        }
    }

    @Test
    void keepsTheStyleCountWhenTheReaderFailsToClose() throws XMLStreamException {
        XMLStreamReader reader = mock(XMLStreamReader.class);
        when(reader.hasNext()).thenReturn(true, false);
        when(reader.next()).thenReturn(XMLStreamConstants.START_ELEMENT);
        when(reader.getLocalName()).thenReturn("style");
        doThrow(new XMLStreamException("Cannot close the reader")).when(reader).close();

        XMLInputFactory factory = mock(XMLInputFactory.class);
        when(factory.createXMLStreamReader(any(InputStream.class))).thenReturn(reader);
        byte[] archive = docx(Map.of(STYLES_ENTRY, STYLES_XML));

        try (MockedStatic<XMLInputFactory> factories = mockStatic(XMLInputFactory.class)) {
            factories.when(XMLInputFactory::newFactory).thenReturn(factory);

            assertEquals(1, inspector.inspect(archive).getStyleCount());
        }
    }

    @Test
    void rejectsAnEmptyUpload() {
        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(null));
    }

    /**
     * An archive whose single entry inflates past the 16 MiB cap the inspector enforces per entry.
     */
    private static byte[] docxWithOversizedStylesEntry() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(STYLES_ENTRY));
            byte[] chunk = new byte[1024 * 1024];
            Arrays.fill(chunk, (byte) ' ');
            for (int written = 0; written <= 16 * 1024 * 1024; written += chunk.length) {
                zip.write(chunk);
            }
            zip.closeEntry();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build the test document", e);
        }
        return out.toByteArray();
    }

    private static byte[] docx(Map<String, String> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(entries).entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build the test document", e);
        }
        return out.toByteArray();
    }
}
