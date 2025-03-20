package ch.sbb.polarion.extension.docx_exporter;

import ch.sbb.polarion.extension.generic.GenericUiServlet;

import java.io.Serial;

public class DocxExporterUiServlet extends GenericUiServlet {
    @Serial
    private static final long serialVersionUID = 8956634237920845540L;

    public DocxExporterUiServlet() {
        super("docx-exporter");
    }
}
