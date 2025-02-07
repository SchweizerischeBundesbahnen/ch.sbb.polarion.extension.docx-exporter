package ch.sbb.polarion.extension.docx_exporter;

import ch.sbb.polarion.extension.generic.GenericUiServlet;

import java.io.Serial;

public class PdfExporterAdminUiServlet extends GenericUiServlet {

    @Serial
    private static final long serialVersionUID = -6337912330074718317L;

    public PdfExporterAdminUiServlet() {
        super("docx-exporter-admin");
    }
}
