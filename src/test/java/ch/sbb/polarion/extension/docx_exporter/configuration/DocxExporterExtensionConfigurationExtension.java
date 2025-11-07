package ch.sbb.polarion.extension.docx_exporter.configuration;

import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import ch.sbb.polarion.extension.generic.test_extensions.CustomExtensionMockInjector;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.Mockito.mockStatic;

public class DocxExporterExtensionConfigurationExtension implements BeforeEachCallback, AfterEachCallback {

    private MockedStatic<DocxExporterExtensionConfiguration> docxExporterExtensionConfigurationMockedStatic;

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        DocxExporterExtensionConfiguration docxExporterExtensionConfiguration = Mockito.mock(DocxExporterExtensionConfiguration.class);
        CustomExtensionMockInjector.inject(extensionContext, docxExporterExtensionConfiguration);
        docxExporterExtensionConfigurationMockedStatic = mockStatic(DocxExporterExtensionConfiguration.class);
        docxExporterExtensionConfigurationMockedStatic.when(DocxExporterExtensionConfiguration::getInstance).thenReturn(docxExporterExtensionConfiguration);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        docxExporterExtensionConfigurationMockedStatic.close();
    }
}
