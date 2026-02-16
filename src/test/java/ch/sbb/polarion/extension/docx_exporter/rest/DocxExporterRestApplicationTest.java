package ch.sbb.polarion.extension.docx_exporter.rest;

import ch.sbb.polarion.extension.docx_exporter.configuration.DocxExporterExtensionConfigurationExtension;
import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import ch.sbb.polarion.extension.docx_exporter.util.DocxExporterFileResourceProvider;
import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.test_extensions.CustomExtensionMock;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mockConstruction;

@ExtendWith({MockitoExtension.class, CurrentContextExtension.class, DocxExporterExtensionConfigurationExtension.class, PlatformContextMockExtension.class})
class DocxExporterRestApplicationTest {

    @SuppressWarnings("unused")
    @CustomExtensionMock
    private DocxExporterExtensionConfiguration docxExporterExtensionConfiguration;

    @Test
    @SuppressWarnings("unused")
    void testInitialization() {
        try (MockedConstruction<DocxExporterFileResourceProvider> resourceProviderMock = mockConstruction(DocxExporterFileResourceProvider.class)) {
            DocxExporterRestApplication application = new DocxExporterRestApplication();
            assertDoesNotThrow(application::getExtensionControllerClasses);
            assertDoesNotThrow(application::getExtensionExceptionMapperSingletons);
            assertDoesNotThrow(application::getExtensionFilterSingletons);
        }
    }

}
