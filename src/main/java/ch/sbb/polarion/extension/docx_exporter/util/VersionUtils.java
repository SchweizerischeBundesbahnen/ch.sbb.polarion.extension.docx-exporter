package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.util.exporter.Constants;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class VersionUtils {

    public static @Nullable String getLatestCompatibleVersionPandocService() {
        return ch.sbb.polarion.extension.generic.util.VersionUtils.getValueFromProperties(Constants.VERSION_FILE, "pandoc-service.version");
    }

}
