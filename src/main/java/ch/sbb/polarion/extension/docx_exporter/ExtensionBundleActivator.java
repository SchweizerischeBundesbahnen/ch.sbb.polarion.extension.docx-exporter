package ch.sbb.polarion.extension.docx_exporter;

import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverterJobsService;
import ch.sbb.polarion.extension.generic.GenericBundleActivator;
import com.polarion.alm.ui.server.forms.extensions.IFormExtension;
import org.osgi.framework.BundleContext;

import java.util.Map;

public class ExtensionBundleActivator extends GenericBundleActivator {

    @Override
    protected Map<String, IFormExtension> getExtensions() {
        return Map.of("docx-exporter", new DocxExporterFormExtension());
    }

    @Override
    public void stop(BundleContext context) {
        DocxConverterJobsService.shutdown();
        super.stop(context);
    }

}
