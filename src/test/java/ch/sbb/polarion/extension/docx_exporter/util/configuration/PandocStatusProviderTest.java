package ch.sbb.polarion.extension.docx_exporter.util.configuration;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocInfo;
import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import ch.sbb.polarion.extension.generic.configuration.ConfigurationStatus;
import ch.sbb.polarion.extension.generic.configuration.ConfigurationStatusProvider;
import ch.sbb.polarion.extension.generic.configuration.Status;
import ch.sbb.polarion.extension.generic.util.VersionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.ProcessingException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static ch.sbb.polarion.extension.docx_exporter.util.exporter.Constants.VERSION_FILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class})
class PandocStatusProviderTest {

    private static final String API_VERSION_PROPERTY = "pandoc-service.api-version";

    @Test
    void testHappyPath() {
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_INSTANT);
        PandocInfo pandocInfo = PandocInfo.builder()
                .apiVersion(1)
                .python("3.12.5")
                .timestamp(timestamp)
                .pandoc("3.10")
                .pandocService("2.4.0")
                .chromium("148.0.7778.96")
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(() -> VersionUtils.getValueFromProperties(VERSION_FILE, API_VERSION_PROPERTY)).thenReturn("1");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(4, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.OK, "2.4.0 (" + timestamp + ")"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.OK, "3.12.5"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.OK, "3.10"),
                    new ConfigurationStatus("Pandoc Service: Chromium", Status.OK, "148.0.7778.96")
            );
        }
    }

    @Test
    void testConnectionRefused() {
        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenThrow(new ProcessingException("java.net.ConnectException: Connection refused"));
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

        assertEquals(1, configurationStatuses.size());
        assertThat(configurationStatuses).containsExactlyInAnyOrder(
                new ConfigurationStatus("Pandoc Service", Status.ERROR, "java.net.ConnectException: Connection refused")
        );
    }

    @Test
    void testIncompatibleApiVersion() {
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_INSTANT);
        PandocInfo pandocInfo = PandocInfo.builder()
                .apiVersion(2)
                .python("3.12.5")
                .timestamp(timestamp)
                .pandoc("3.10")
                .pandocService("2.5.0")
                .chromium("148.0.7778.96")
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(() -> VersionUtils.getValueFromProperties(VERSION_FILE, API_VERSION_PROPERTY)).thenReturn("1");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(4, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.WARNING, "2.5.0 (" + timestamp + "): <span style='color: red;'>incompatible API version 2, expected 1</span>"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.OK, "3.12.5"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.OK, "3.10"),
                    new ConfigurationStatus("Pandoc Service: Chromium", Status.OK, "148.0.7778.96")
            );
        }
    }

    @Test
    void testUnknownApiVersion() {
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_INSTANT);
        PandocInfo pandocInfo = PandocInfo.builder()
                .apiVersion(null)
                .python("3.12.5")
                .timestamp(timestamp)
                .pandoc("3.10")
                .pandocService("2.3.4")
                .chromium("148.0.7778.96")
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(() -> VersionUtils.getValueFromProperties(VERSION_FILE, API_VERSION_PROPERTY)).thenReturn("1");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(4, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.ERROR, "2.3.4 (" + timestamp + "): <span style='color: red;'>API version unknown, please upgrade pandoc-service</span>"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.OK, "3.12.5"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.OK, "3.10"),
                    new ConfigurationStatus("Pandoc Service: Chromium", Status.OK, "148.0.7778.96")
            );
        }
    }

    @Test
    void testUnknownApiVersionNoTimestamp() {
        PandocInfo pandocInfo = PandocInfo.builder()
                .apiVersion(null)
                .python("3.12.5")
                .timestamp(null)
                .pandoc("3.10")
                .pandocService(null)
                .chromium("148.0.7778.96")
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(() -> VersionUtils.getValueFromProperties(VERSION_FILE, API_VERSION_PROPERTY)).thenReturn("1");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(4, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.ERROR, "Unknown: <span style='color: red;'>API version unknown, please upgrade pandoc-service</span>"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.OK, "3.12.5"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.OK, "3.10"),
                    new ConfigurationStatus("Pandoc Service: Chromium", Status.OK, "148.0.7778.96")
            );
        }
    }

    @Test
    void testNoTimestamp() {
        PandocInfo pandocInfo = PandocInfo.builder()
                .apiVersion(1)
                .python("3.12.5")
                .timestamp("")
                .pandoc("3.10")
                .pandocService("2.4.0")
                .chromium("148.0.7778.96")
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(() -> VersionUtils.getValueFromProperties(VERSION_FILE, API_VERSION_PROPERTY)).thenReturn("1");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(4, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.OK, "2.4.0"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.OK, "3.12.5"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.OK, "3.10"),
                    new ConfigurationStatus("Pandoc Service: Chromium", Status.OK, "148.0.7778.96")
            );
        }
    }

    @Test
    void testDifferentServiceVersionSameApiVersion() {
        // Different service releases reporting the same API version stay compatible - no warning
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_INSTANT);
        PandocInfo pandocInfo = PandocInfo.builder()
                .apiVersion(1)
                .python("3.12.5")
                .timestamp(timestamp)
                .pandoc("3.11")
                .pandocService("2.6.1")
                .chromium("148.0.7778.96")
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(() -> VersionUtils.getValueFromProperties(VERSION_FILE, API_VERSION_PROPERTY)).thenReturn("1");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(4, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.OK, "2.6.1 (" + timestamp + ")"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.OK, "3.12.5"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.OK, "3.11"),
                    new ConfigurationStatus("Pandoc Service: Chromium", Status.OK, "148.0.7778.96")
            );
        }
    }

    @Test
    void testExpectedApiVersionNotConfigured() {
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_INSTANT);
        PandocInfo pandocInfo = PandocInfo.builder()
                .apiVersion(1)
                .python("3.12.5")
                .timestamp(timestamp)
                .pandoc("3.10")
                .pandocService("2.4.0")
                .chromium("148.0.7778.96")
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(() -> VersionUtils.getValueFromProperties(VERSION_FILE, API_VERSION_PROPERTY)).thenReturn(null);

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(4, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.WARNING, "2.4.0 (" + timestamp + "): <span style='color: orange;'>expected API version not configured</span>"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.OK, "3.12.5"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.OK, "3.10"),
                    new ConfigurationStatus("Pandoc Service: Chromium", Status.OK, "148.0.7778.96")
            );
        }
    }

    @Test
    void testInvalidExpectedApiVersionConfiguration() {
        PandocInfo pandocInfo = PandocInfo.builder()
                .apiVersion(1)
                .python("3.12.5")
                .timestamp(null)
                .pandoc("3.10")
                .pandocService("2.4.0")
                .chromium("148.0.7778.96")
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(() -> VersionUtils.getValueFromProperties(VERSION_FILE, API_VERSION_PROPERTY)).thenReturn("not-a-number");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(1, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.ERROR, "Invalid configuration for 'pandoc-service.api-version': 'not-a-number' is not a valid integer.")
            );
        }
    }

    @Test
    void testMissingServiceInfo() {
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_INSTANT);
        PandocInfo pandocInfo = PandocInfo.builder()
                .apiVersion(1)
                .python(null)
                .timestamp(timestamp)
                .pandoc("")
                .pandocService("2.4.0")
                .chromium(null)
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(() -> VersionUtils.getValueFromProperties(VERSION_FILE, API_VERSION_PROPERTY)).thenReturn("1");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(4, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.OK, "2.4.0 (" + timestamp + ")"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.ERROR, "Unknown"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.ERROR, "Unknown"),
                    new ConfigurationStatus("Pandoc Service: Chromium", Status.ERROR, "Unknown")
            );
        }
    }

    @Test
    void reportsAKeyConfiguredForAPlainHttpAddress() {
        // /version carries no key, so the service answers and the page would look healthy while every
        // export is refused. The configuration page is where an administrator looks first.
        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocServiceBaseUrl()).thenReturn("http://localhost:9082");
        List<ConfigurationStatus> statuses = new PandocStatusProvider(pandocServiceConnector, () -> "pandoc-api-key")
                .getStatuses(ConfigurationStatusProvider.Context.builder().build());

        assertEquals(1, statuses.size());
        assertEquals(Status.ERROR, statuses.get(0).getStatus());
        assertThat(statuses.get(0).getDetails())
                .contains(DocxExporterExtensionConfiguration.PANDOC_API_KEY_SECRET)
                .contains(DocxExporterExtensionConfiguration.PANDOC_SERVICE)
                .contains("every export is refused");
        verify(pandocServiceConnector, never()).getPandocInfo();
    }

    @Test
    void asksTheServiceWhereTheKeyTravelsOverHttps() {
        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocServiceBaseUrl()).thenReturn("https://pandoc.intranet:9082");
        when(pandocServiceConnector.getPandocInfo()).thenReturn(PandocInfo.builder().apiVersion(1).python("3.12.5").pandoc("3.10").pandocService("2.4.0").chromium("148").build());
        try (MockedStatic<VersionUtils> versions = mockStatic(VersionUtils.class)) {
            versions.when(() -> VersionUtils.getValueFromProperties(VERSION_FILE, API_VERSION_PROPERTY)).thenReturn("1");

            assertEquals(4, new PandocStatusProvider(pandocServiceConnector, () -> "pandoc-api-key")
                    .getStatuses(ConfigurationStatusProvider.Context.builder().build()).size());
        }
    }
}
