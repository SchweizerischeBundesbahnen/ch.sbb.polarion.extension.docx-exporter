package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.PictureDescription;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.DocxStructureInspector.pictureDescriptions;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DocxStructureInspectorTest {

    @Test
    void pictureDescriptionsTellMissingElementFromMissingAttributeAndSkipOtherDrawings() throws IOException {
        byte[] docx = docx(
                "<w:p><w:r><w:drawing><wp:inline><wp:docPr id=\"1\" descr=\"Alt\"/>"
                        + "<a:graphic><a:graphicData><pic:pic><pic:nvPicPr><pic:cNvPr id=\"2\" descr=\"\"/></pic:nvPicPr></pic:pic></a:graphicData></a:graphic>"
                        + "</wp:inline></w:drawing></w:r></w:p>"
                        + "<w:p><w:r><w:drawing><wp:inline><wp:docPr id=\"3\"/>"
                        + "<a:graphic><a:graphicData><pic:pic><pic:nvPicPr><pic:cNvPr id=\"4\"/></pic:nvPicPr></pic:pic></a:graphicData></a:graphic>"
                        + "</wp:inline></w:drawing></w:r></w:p>"
                        + "<w:p><w:r><w:drawing><wp:inline><wp:docPr id=\"5\" descr=\"\"/>"
                        + "<a:graphic><a:graphicData><pic:pic><pic:blipFill/></pic:pic></a:graphicData></a:graphic>"
                        + "</wp:inline></w:drawing></w:r></w:p>"
                        + "<w:p><w:r><w:drawing><wp:inline><wp:docPr id=\"6\" descr=\"Chart\"/>"
                        + "<a:graphic><a:graphicData><c:chart r:id=\"rId1\"/></a:graphicData></a:graphic>"
                        + "</wp:inline></w:drawing></w:r></w:p>");

        assertEquals(List.of(
                new PictureDescription("Alt", ""),
                new PictureDescription("", ""),
                new PictureDescription("", null)), pictureDescriptions(docx));
    }

    private static byte[] docx(String body) throws IOException {
        String documentXml = "<w:document"
                + " xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
                + " xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\""
                + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
                + " xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\""
                + " xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<w:body>" + body + "</w:body></w:document>";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(documentXml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
