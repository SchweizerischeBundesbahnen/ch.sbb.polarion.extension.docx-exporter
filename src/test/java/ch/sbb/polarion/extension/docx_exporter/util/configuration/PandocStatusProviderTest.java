package ch.sbb.polarion.extension.docx_exporter.util.configuration;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocInfo;
import ch.sbb.polarion.extension.generic.configuration.ConfigurationStatus;
import ch.sbb.polarion.extension.generic.configuration.ConfigurationStatusProvider;
import ch.sbb.polarion.extension.generic.configuration.Status;
import ch.sbb.polarion.extension.docx_exporter.util.VersionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.ProcessingException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class})
class PandocStatusProviderTest {

    @Test
    void testHappyPath() {
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_INSTANT);
        PandocInfo pandocInfo = PandocInfo.builder()
                .python("3.12.5")
                .timestamp(timestamp)
                .pandoc("2.4")
                .pandocService("1.0.0")
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(VersionUtils::getLatestCompatibleVersionPandocService).thenReturn("1.0.0");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(3, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.OK, "1.0.0"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.OK, "3.12.5"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.OK, "2.4")
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
    void testUpdatePandocRequired() {
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_INSTANT);
        PandocInfo pandocInfo = PandocInfo.builder()
                .python("3.12.5")
                .timestamp(timestamp)
                .pandoc("2.4")
                .pandocService("1.0.0")
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(VersionUtils::getLatestCompatibleVersionPandocService).thenReturn("1.0.1");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(3, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.WARNING, "1.0.0 (" + timestamp + "): <span style='color: red;'>use latest compatible</span> <a href='https://github.com/SchweizerischeBundesbahnen/pandoc-service/releases/tag/v1.0.1' target='_blank'>1.0.1</a>"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.OK, "3.12.5"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.OK, "2.4")
            );
        }
    }

    @Test
    void testUnknownPandocServiceVersion() {
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_INSTANT);
        PandocInfo pandocInfo = PandocInfo.builder()
                .python("3.12.5")
                .timestamp(timestamp)
                .pandoc("2.4")
                .pandocService(null)
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(VersionUtils::getLatestCompatibleVersionPandocService).thenReturn("1.0.1");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(3, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.ERROR, "Unknown (" + timestamp + "): <span style='color: red;'>use latest compatible</span> <a href='https://github.com/SchweizerischeBundesbahnen/pandoc-service/releases/tag/v1.0.1' target='_blank'>1.0.1</a>"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.OK, "3.12.5"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.OK, "2.4")
            );
        }
    }

    @Test
    void testUnknownPandocServiceVersionNoTimestamp() {
        PandocInfo pandocInfo = PandocInfo.builder()
                .python("3.12.5")
                .timestamp(null)
                .pandoc("2.4")
                .pandocService(null)
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(VersionUtils::getLatestCompatibleVersionPandocService).thenReturn("1.0.1");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(3, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.ERROR, "Unknown: <span style='color: red;'>use latest compatible</span> <a href='https://github.com/SchweizerischeBundesbahnen/pandoc-service/releases/tag/v1.0.1' target='_blank'>1.0.1</a>"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.OK, "3.12.5"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.OK, "2.4")
            );
        }
    }

    @Test
    void testNoTimestamp() {
        PandocInfo pandocInfo = PandocInfo.builder()
                .python("3.12.5")
                .timestamp("")
                .pandoc("2.4")
                .pandocService("1.0.0")
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(VersionUtils::getLatestCompatibleVersionPandocService).thenReturn("1.0.1");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(3, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.WARNING, "1.0.0: <span style='color: red;'>use latest compatible</span> <a href='https://github.com/SchweizerischeBundesbahnen/pandoc-service/releases/tag/v1.0.1' target='_blank'>1.0.1</a>"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.OK, "3.12.5"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.OK, "2.4")
            );
        }
    }

    @Test
    void testPandocVersionIsHigherThanRequired() {
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_INSTANT);
        PandocInfo pandocInfo = PandocInfo.builder()
                .python("3.12.5")
                .timestamp(timestamp)
                .pandoc("2.4")
                .pandocService("1.0.5")
                .build();

        PandocServiceConnector pandocServiceConnector = mock(PandocServiceConnector.class);
        when(pandocServiceConnector.getPandocInfo()).thenReturn(pandocInfo);
        PandocStatusProvider pandocStatusProvider = new PandocStatusProvider(pandocServiceConnector);

        try (MockedStatic<VersionUtils> versionsUtilsMockedStatic = mockStatic(VersionUtils.class)) {
            versionsUtilsMockedStatic.when(VersionUtils::getLatestCompatibleVersionPandocService).thenReturn("1.0.1");

            List<ConfigurationStatus> configurationStatuses = pandocStatusProvider.getStatuses(ConfigurationStatusProvider.Context.builder().build());

            assertEquals(3, configurationStatuses.size());
            assertThat(configurationStatuses).containsExactlyInAnyOrder(
                    new ConfigurationStatus("Pandoc Service", Status.WARNING, "1.0.5 (" + timestamp + "): <span style='color: red;'>use latest compatible</span> <a href='https://github.com/SchweizerischeBundesbahnen/pandoc-service/releases/tag/v1.0.1' target='_blank'>1.0.1</a>"),
                    new ConfigurationStatus("Pandoc Service: Python", Status.OK, "3.12.5"),
                    new ConfigurationStatus("Pandoc Service: Pandoc", Status.OK, "2.4")
            );
        }
    }

}
