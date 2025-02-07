package ch.sbb.polarion.extension.docx_exporter.configuration;

import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.Mockito.mockStatic;

public class PdfExporterExtensionConfigurationExtension implements BeforeEachCallback, AfterEachCallback {

    private MockedStatic<DocxExporterExtensionConfiguration> pdfExporterExtensionConfigurationMockedStatic;

    private static DocxExporterExtensionConfiguration pdfExporterExtensionConfiguration;

    public static void setPdfExporterExtensionConfigurationMock(DocxExporterExtensionConfiguration mock) {
        pdfExporterExtensionConfiguration = mock;
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        if (pdfExporterExtensionConfiguration == null) {
            pdfExporterExtensionConfiguration = Mockito.mock(DocxExporterExtensionConfiguration.class);
        }

        pdfExporterExtensionConfigurationMockedStatic = mockStatic(DocxExporterExtensionConfiguration.class);
        pdfExporterExtensionConfigurationMockedStatic.when(DocxExporterExtensionConfiguration::getInstance).thenReturn(pdfExporterExtensionConfiguration);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        if (pdfExporterExtensionConfigurationMockedStatic != null) {
            pdfExporterExtensionConfigurationMockedStatic.close();
        }
        pdfExporterExtensionConfiguration = null;
    }
}
