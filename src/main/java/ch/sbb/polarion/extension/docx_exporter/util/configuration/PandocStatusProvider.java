package ch.sbb.polarion.extension.docx_exporter.util.configuration;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.PandocServiceConnector;
import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocInfo;
import ch.sbb.polarion.extension.generic.configuration.ConfigurationStatus;
import ch.sbb.polarion.extension.generic.configuration.ConfigurationStatusProvider;
import ch.sbb.polarion.extension.generic.configuration.Status;
import ch.sbb.polarion.extension.generic.util.Discoverable;
import ch.sbb.polarion.extension.docx_exporter.util.VersionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

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
    }

    private static final Map<PandocServiceInfo, String> PANDOC_SERVICE_INFO = Map.of(
            PandocServiceInfo.VERSION, "Pandoc Service",
            PandocServiceInfo.PYTHON, "Pandoc Service: Python",
            PandocServiceInfo.PANDOC, "Pandoc Service: Pandoc"
    );

    @Override
    public @NotNull List<ConfigurationStatus> getStatuses(@NotNull Context context) {
        try {
            PandocInfo pandocInfo = pandocServiceConnector.getPandocInfo();
            return List.of(
                    createPandocStatus(PANDOC_SERVICE_INFO.get(PandocServiceInfo.VERSION), pandocInfo.getPandocService(), pandocInfo.getTimestamp(), VersionUtils.getLatestCompatibleVersionPandocService()),
                    createPandocStatus(PANDOC_SERVICE_INFO.get(PandocServiceInfo.PYTHON), pandocInfo.getPython()),
                    createPandocStatus(PANDOC_SERVICE_INFO.get(PandocServiceInfo.PANDOC), pandocInfo.getPandoc())
            );
        } catch (Exception e) {
            return List.of(new ConfigurationStatus(PANDOC_SERVICE_INFO.get(PandocServiceInfo.VERSION), Status.ERROR, e.getMessage()));
        }
    }

    private static @NotNull ConfigurationStatus createPandocStatus(@NotNull String name, @Nullable String version) {
        if (version == null || version.isBlank()) {
            return new ConfigurationStatus(name, Status.ERROR, "Unknown");
        } else {
            return new ConfigurationStatus(name, Status.OK, version);
        }
    }

    private static @NotNull ConfigurationStatus createPandocStatus(@NotNull String name, @Nullable String version, @Nullable String timestamp, @Nullable String latestCompatibleVersion) {
        if (version == null || version.isBlank()) {
            return new ConfigurationStatus(name, Status.ERROR, createUseLatestCompatiblePandocMessage("Unknown", timestamp, latestCompatibleVersion));
        } else if (!version.equals(latestCompatibleVersion)) {
            return new ConfigurationStatus(name, Status.WARNING, createUseLatestCompatiblePandocMessage(version, timestamp, latestCompatibleVersion));
        } else {
            return new ConfigurationStatus(name, Status.OK, version);
        }
    }

    private static @NotNull String createUseLatestCompatiblePandocMessage(@NotNull String version, @Nullable String timestamp, @Nullable String latestCompatibleVersion) {
        StringBuilder message = new StringBuilder();
        message.append(version);
        if (timestamp != null && !timestamp.isBlank()) {
            message.append(" (").append(timestamp).append(")");
        }
        if (latestCompatibleVersion != null && !latestCompatibleVersion.isBlank()) {
            message.append(": <span style='color: red;'>use latest compatible</span> <a href='https://github.com/SchweizerischeBundesbahnen/pandoc-service/releases/tag/v").append(latestCompatibleVersion).append("' target='_blank'>").append(latestCompatibleVersion).append("</a>");
        }
        return message.toString();
    }

}
