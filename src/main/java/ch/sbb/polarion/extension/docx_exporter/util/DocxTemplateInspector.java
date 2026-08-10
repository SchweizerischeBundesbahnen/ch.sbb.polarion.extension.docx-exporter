package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.model.TemplateDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads the two facts the Templates administration page displays about an uploaded DOCX reference
 * template: how many styles it defines and when it was last modified. It doubles as the validator of
 * that upload - anything that is not a readable DOCX makes {@link #inspect(byte[])} throw.
 * <p>
 * This used to run in the browser on JSZip. It moved here so the administration page needs no zip
 * library of its own, and so "is this a valid docx" is answered by the same runtime that later has to
 * hand the file to pandoc.
 */
public class DocxTemplateInspector {

    private static final String STYLES_ENTRY = "word/styles.xml";
    private static final String CORE_PROPERTIES_ENTRY = "docProps/core.xml";
    private static final String STYLE_ELEMENT = "style";
    private static final String MODIFIED_ELEMENT = "modified";

    /**
     * Cap on a single decompressed entry. Neither of the two entries read here is anywhere near it in a
     * real document, so the only thing it bounds is a crafted archive that inflates without limit.
     */
    private static final int MAX_ENTRY_BYTES = 16 * 1024 * 1024;

    public @NotNull TemplateDetails inspect(byte @Nullable [] docx) {
        if (docx == null || docx.length == 0) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        byte[] stylesXml = null;
        byte[] corePropertiesXml = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (STYLES_ENTRY.equals(entry.getName())) {
                    stylesXml = readEntry(zip);
                } else if (CORE_PROPERTIES_ENTRY.equals(entry.getName())) {
                    corePropertiesXml = readEntry(zip);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Uploaded file is not a readable docx archive", e);
        }

        if (stylesXml == null) {
            throw new IllegalArgumentException("Uploaded file contains no " + STYLES_ENTRY);
        }

        return TemplateDetails.builder()
                .styleCount(countStyles(stylesXml))
                .modifiedDate(corePropertiesXml == null ? null : readModifiedDate(corePropertiesXml))
                .build();
    }

    private byte @NotNull [] readEntry(@NotNull ZipInputStream zip) throws IOException {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) > 0) {
            if (content.size() + read > MAX_ENTRY_BYTES) {
                throw new IOException("Entry exceeds the maximum size of " + MAX_ENTRY_BYTES + " bytes");
            }
            content.write(buffer, 0, read);
        }
        return content.toByteArray();
    }

    /**
     * The number of {@code w:style} elements, which appear only as children of the {@code w:styles} root.
     * Matched by local name, so a document declaring the WordprocessingML namespace under another prefix
     * counts the same.
     */
    private int countStyles(byte @NotNull [] stylesXml) {
        int count = 0;
        XMLStreamReader reader = null;
        try {
            reader = newReader(stylesXml);
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT && STYLE_ELEMENT.equals(reader.getLocalName())) {
                    count++;
                }
            }
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("Uploaded file contains an unreadable " + STYLES_ENTRY, e);
        } finally {
            close(reader);
        }
        return count;
    }

    /**
     * The {@code dcterms:modified} timestamp, rendered the way the administration page shows it: the ISO
     * date and time separated by a space, without the trailing zone marker.
     */
    private @Nullable String readModifiedDate(byte @NotNull [] corePropertiesXml) {
        XMLStreamReader reader = null;
        try {
            reader = newReader(corePropertiesXml);
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT && MODIFIED_ELEMENT.equals(reader.getLocalName())) {
                    String modified = reader.getElementText();
                    return modified.isBlank() ? null : modified.replace('T', ' ').replace("Z", "").trim();
                }
            }
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("Uploaded file contains an unreadable " + CORE_PROPERTIES_ENTRY, e);
        } finally {
            close(reader);
        }
        return null;
    }

    private @NotNull XMLStreamReader newReader(byte @NotNull [] xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        // No DTDs and no external entities: this XML arrives from an uploaded file.
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory.createXMLStreamReader(new ByteArrayInputStream(xml));
    }

    private void close(@Nullable XMLStreamReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (XMLStreamException e) {
                // Nothing to release beyond the in-memory stream this reader was created over.
            }
        }
    }
}
