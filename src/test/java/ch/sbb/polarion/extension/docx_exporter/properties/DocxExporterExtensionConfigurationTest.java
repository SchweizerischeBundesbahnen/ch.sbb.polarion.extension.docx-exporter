package ch.sbb.polarion.extension.docx_exporter.properties;

import ch.sbb.polarion.extension.generic.util.ContextUtils;
import com.polarion.core.config.impl.SystemValueReader;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DocxExporterExtensionConfigurationTest {

    private static final String PREFIX = "ch.sbb.polarion.extension.docx-exporter.";
    private static final String TEMPLATE_MAX_SIZE_MB_PROPERTY = PREFIX + "templateMaxSizeMB";

    @Test
    void readsTheTemplateSizeOutOfPolarionProperties() {
        SystemValueReader reader = mock(SystemValueReader.class);
        when(reader.readInt(TEMPLATE_MAX_SIZE_MB_PROPERTY, 16)).thenReturn(32);

        assertEquals(32, withReader(reader, DocxExporterExtensionConfiguration::getTemplateMaxSizeMB));
    }

    @Test
    void fallsBackToSixteenMegabytesWhenThePropertyIsNotSet() {
        SystemValueReader reader = mock(SystemValueReader.class);
        when(reader.readInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));

        assertEquals(16, withReader(reader, DocxExporterExtensionConfiguration::getTemplateMaxSizeMB));
    }

    @Test
    void offersTheTemplateSizeOnTheConfigurationPage() {
        try (MockedStatic<ContextUtils> contextUtils = mockStatic(ContextUtils.class)) {
            contextUtils.when(ContextUtils::getConfigurationPropertiesPrefix).thenReturn(PREFIX);
            DocxExporterExtensionConfiguration configuration = new DocxExporterExtensionConfiguration();

            assertTrue(configuration.getSupportedProperties().contains(DocxExporterExtensionConfiguration.TEMPLATE_MAX_SIZE_MB));
            assertEquals("16", configuration.getTemplateMaxSizeMBDefaultValue());
            assertEquals(DocxExporterExtensionConfiguration.TEMPLATE_MAX_SIZE_MB_DESCRIPTION, configuration.getTemplateMaxSizeMBDescription());
        }
    }

    private static <T> T withReader(SystemValueReader reader, Function<DocxExporterExtensionConfiguration, T> read) {
        try (MockedStatic<ContextUtils> contextUtils = mockStatic(ContextUtils.class);
             MockedStatic<SystemValueReader> readers = mockStatic(SystemValueReader.class)) {
            contextUtils.when(ContextUtils::getConfigurationPropertiesPrefix).thenReturn(PREFIX);
            readers.when(SystemValueReader::getInstance).thenReturn(reader);
            return read.apply(new DocxExporterExtensionConfiguration());
        }
    }
}
