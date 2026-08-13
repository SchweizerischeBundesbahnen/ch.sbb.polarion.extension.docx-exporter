package ch.sbb.polarion.extension.docx_exporter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocxExporterAppServletTest {

    @Test
    void testConstruction() {
        assertDoesNotThrow(DocxExporterAppServlet::new);
    }

}
