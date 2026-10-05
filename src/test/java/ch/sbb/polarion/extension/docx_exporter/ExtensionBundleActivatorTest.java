package ch.sbb.polarion.extension.docx_exporter;

import ch.sbb.polarion.extension.docx_exporter.converter.DocxConverterJobsService;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.osgi.framework.BundleContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(PlatformContextMockExtension.class)
class ExtensionBundleActivatorTest {

    @Test
    void testBundleActivator() {
        assertEquals("docx-exporter", new ExtensionBundleActivator().getExtensions().keySet().iterator().next());
    }

    /**
     * The conversion threads and the cleaner of finished conversions stop with the bundle.
     */
    @Test
    void testTheConversionJobsStopWithTheBundle() {
        try (MockedStatic<DocxConverterJobsService> jobsService = mockStatic(DocxConverterJobsService.class)) {
            new ExtensionBundleActivator().stop(mock(BundleContext.class));

            jobsService.verify(DocxConverterJobsService::shutdown);
        }
    }

}
