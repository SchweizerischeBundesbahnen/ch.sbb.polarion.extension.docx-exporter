package ch.sbb.polarion.extension.docx_exporter;

import ch.sbb.polarion.extension.generic.GenericUiServlet;

import java.io.Serial;

/**
 * Serves the React administration app (the Vite bundle under {@code webapp/docx-exporter-app}) and the
 * build-generated help articles next to it. The pages still on JSP keep being served by
 * {@link DocxExporterAdminUiServlet} until they are converted too.
 */
public class DocxExporterAppServlet extends GenericUiServlet {
    @Serial
    private static final long serialVersionUID = 7451028364759201847L;

    public DocxExporterAppServlet() {
        super("docx-exporter-app");
    }
}
