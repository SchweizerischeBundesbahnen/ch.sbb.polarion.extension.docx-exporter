package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.docx_exporter.util.configuration.PandocStatusProvider;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@UtilityClass
public class VersionUtils {

    public static @Nullable String getLatestCompatibleVersionPandocService() {
        try (InputStream input = PandocStatusProvider.class.getClassLoader().getResourceAsStream("versions.properties")) {
            if (input == null) {
                return null;
            }

            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty("pandoc-service.version");
        } catch (IOException e) {
            return null;
        }
    }

}
