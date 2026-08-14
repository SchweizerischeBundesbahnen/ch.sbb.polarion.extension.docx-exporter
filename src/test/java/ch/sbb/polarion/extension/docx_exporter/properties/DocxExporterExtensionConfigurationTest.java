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

    @Test
    void readsTheExternalResourcePolicyOutOfPolarionProperties() {
        SystemValueReader reader = mock(SystemValueReader.class);
        when(reader.readString(PREFIX + "externalResources.policy", "blockInternal")).thenReturn("allowlistOnly");
        when(reader.readString(PREFIX + "externalResources.allowedHosts", "")).thenReturn("cdn.intranet");
        when(reader.readInt(PREFIX + "externalResources.maxSizeMB", 16)).thenReturn(32);

        assertEquals("allowlistOnly", withReader(reader, DocxExporterExtensionConfiguration::getExternalResourcesPolicy));
        assertEquals("cdn.intranet", withReader(reader, DocxExporterExtensionConfiguration::getExternalResourcesAllowedHosts));
        assertEquals(32, withReader(reader, DocxExporterExtensionConfiguration::getExternalResourcesMaxSizeMB));
    }

    @Test
    void offersTheExternalResourcePropertiesOnTheConfigurationPage() {
        try (MockedStatic<ContextUtils> contextUtils = mockStatic(ContextUtils.class)) {
            contextUtils.when(ContextUtils::getConfigurationPropertiesPrefix).thenReturn(PREFIX);
            DocxExporterExtensionConfiguration configuration = new DocxExporterExtensionConfiguration();

            assertTrue(configuration.getSupportedProperties().contains(DocxExporterExtensionConfiguration.EXTERNAL_RESOURCES_POLICY));
            assertTrue(configuration.getSupportedProperties().contains(DocxExporterExtensionConfiguration.EXTERNAL_RESOURCES_ALLOWED_HOSTS));
            assertTrue(configuration.getSupportedProperties().contains(DocxExporterExtensionConfiguration.EXTERNAL_RESOURCES_MAX_SIZE_MB));
            assertEquals("blockInternal", configuration.getExternalResourcesPolicyDefaultValue());
            assertEquals("", configuration.getExternalResourcesAllowedHostsDefaultValue());
            assertEquals("16", configuration.getExternalResourcesMaxSizeMBDefaultValue());
            assertEquals(DocxExporterExtensionConfiguration.EXTERNAL_RESOURCES_POLICY_DESCRIPTION, configuration.getExternalResourcesPolicyDescription());
            assertEquals(DocxExporterExtensionConfiguration.EXTERNAL_RESOURCES_ALLOWED_HOSTS_DESCRIPTION, configuration.getExternalResourcesAllowedHostsDescription());
            assertEquals(DocxExporterExtensionConfiguration.EXTERNAL_RESOURCES_MAX_SIZE_MB_DESCRIPTION, configuration.getExternalResourcesMaxSizeMBDescription());
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
