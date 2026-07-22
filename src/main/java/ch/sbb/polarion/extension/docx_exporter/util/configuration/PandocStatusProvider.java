package ch.sbb.polarion.extension.docx_exporter.util.configuration;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocInfo;
import ch.sbb.polarion.extension.generic.configuration.ConfigurationStatus;
import ch.sbb.polarion.extension.generic.configuration.ConfigurationStatusProvider;
import ch.sbb.polarion.extension.generic.configuration.Status;
import ch.sbb.polarion.extension.generic.util.Discoverable;
import ch.sbb.polarion.extension.generic.util.VersionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static ch.sbb.polarion.extension.docx_exporter.util.exporter.Constants.VERSION_FILE;

@Discoverable
public class PandocStatusProvider extends ConfigurationStatusProvider {

    private final PandocServiceConnector pandocServiceConnector;

    public PandocStatusProvider() {
        this.pandocServiceConnector = new PandocServiceConnector();
    }

    public PandocStatusProvider(PandocServiceConnector pandocServiceConnector) {
        this.pandocServiceConnector = pandocServiceConnector;
    }

    private enum PandocServiceInfo {
        VERSION,
        PYTHON,
        PANDOC,
        CHROMIUM,
    }

    private static final Map<PandocServiceInfo, String> PANDOC_SERVICE_INFO = Map.of(
            PandocServiceInfo.VERSION, "Pandoc Service",
            PandocServiceInfo.PYTHON, "Pandoc Service: Python",
            PandocServiceInfo.PANDOC, "Pandoc Service: Pandoc",
            PandocServiceInfo.CHROMIUM, "Pandoc Service: Chromium"
    );

    @Override
    public @NotNull List<ConfigurationStatus> getStatuses(@NotNull Context context) {
        try {
            PandocInfo pandocInfo = pandocServiceConnector.getPandocInfo();
            String expectedApiVersionStr = VersionUtils.getValueFromProperties(VERSION_FILE, "pandoc-service.api-version");
            Integer expectedApiVersion = parseExpectedApiVersion(expectedApiVersionStr);
            return List.of(
                    createPandocVersionStatus(
                            PANDOC_SERVICE_INFO.get(PandocServiceInfo.VERSION),
                            pandocInfo.getPandocService(),
                            pandocInfo.getTimestamp(),
                            pandocInfo.getApiVersion(),
                            expectedApiVersion),
                    createPandocStatus(PANDOC_SERVICE_INFO.get(PandocServiceInfo.PYTHON), pandocInfo.getPython()),
                    createPandocStatus(PANDOC_SERVICE_INFO.get(PandocServiceInfo.PANDOC), pandocInfo.getPandoc()),
                    createPandocStatus(PANDOC_SERVICE_INFO.get(PandocServiceInfo.CHROMIUM), pandocInfo.getChromium())
            );
        } catch (Exception e) {
            return List.of(new ConfigurationStatus(PANDOC_SERVICE_INFO.get(PandocServiceInfo.VERSION), Status.ERROR, e.getMessage()));
        }
    }

    private static @Nullable Integer parseExpectedApiVersion(@Nullable String expectedApiVersionStr) {
        if (expectedApiVersionStr == null) {
            return null;
        }
        try {
            return Integer.valueOf(expectedApiVersionStr);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(
                    "Invalid configuration for 'pandoc-service.api-version': '" + expectedApiVersionStr + "' is not a valid integer.",
                    nfe
            );
        }
    }

    private static @NotNull ConfigurationStatus createPandocStatus(@NotNull String name, @Nullable String version) {
        if (version == null || version.isBlank()) {
            return new ConfigurationStatus(name, Status.ERROR, "Unknown");
        } else {
            return new ConfigurationStatus(name, Status.OK, version);
        }
    }

    private static @NotNull ConfigurationStatus createPandocVersionStatus(
            @NotNull String name,
            @Nullable String serviceVersion,
            @Nullable String timestamp,
            @Nullable Integer apiVersion,
            @Nullable Integer expectedApiVersion) {

        String displayVersion = formatVersionWithTimestamp(serviceVersion, timestamp);

        if (apiVersion == null) {
            return new ConfigurationStatus(name, Status.ERROR,
                    displayVersion + ": <span style='color: red;'>API version unknown, please upgrade pandoc-service</span>");
        } else if (expectedApiVersion == null) {
            return new ConfigurationStatus(name, Status.WARNING,
                    displayVersion + ": <span style='color: orange;'>expected API version not configured</span>");
        } else if (!apiVersion.equals(expectedApiVersion)) {
            return new ConfigurationStatus(name, Status.WARNING,
                    displayVersion + ": <span style='color: red;'>incompatible API version " + apiVersion + ", expected " + expectedApiVersion + "</span>");
        } else {
            return new ConfigurationStatus(name, Status.OK, displayVersion);
        }
    }

    private static @NotNull String formatVersionWithTimestamp(@Nullable String version, @Nullable String timestamp) {
        StringBuilder message = new StringBuilder();
        message.append(version != null && !version.isBlank() ? version : "Unknown");
        if (timestamp != null && !timestamp.isBlank()) {
            message.append(" (").append(timestamp).append(")");
        }
        return message.toString();
    }
}
