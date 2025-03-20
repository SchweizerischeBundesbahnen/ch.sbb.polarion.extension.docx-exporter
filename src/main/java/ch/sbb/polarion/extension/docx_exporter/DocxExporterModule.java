package ch.sbb.polarion.extension.docx_exporter;

import ch.sbb.polarion.extension.generic.GenericModule;
import com.polarion.alm.ui.server.forms.extensions.FormExtensionContribution;

public class DocxExporterModule extends GenericModule {
    public static final String PDF_EXPORTER_FORM_EXTENSION_ID = "docx-exporter";

    @Override
    protected FormExtensionContribution getFormExtensionContribution() {
        return new FormExtensionContribution(DocxExporterFormExtension.class, PDF_EXPORTER_FORM_EXTENSION_ID);
    }
}
