package ch.sbb.polarion.extension.docx_exporter.pandoc.service;

import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import com.polarion.core.util.vault.PolarionSecretsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Supplies the API key of the pandoc service.
 * <p>
 * The key itself is never configured in {@code polarion.properties}. The property
 * {@code pandoc.apiKeySecret} holds the <em>name</em> of a Polarion secret, and the value behind that
 * name is read here, so neither the properties file nor the About page carries the credential.
 * <p>
 * An unset name means the service needs no key, which is how the service is configured by default.
 */
public class ApiKeyProvider {

    /**
     * Reads the API key configured for the pandoc service.
     *
     * @return the key, or {@code null} when no secret name is configured
     * @throws IllegalStateException if a name is configured but no value can be read for it
     */
    public @Nullable String getApiKey() {
        String configured = DocxExporterExtensionConfiguration.getInstance().getPandocApiKeySecret();
        String secretName = configured == null ? "" : configured.trim();
        if (secretName.isEmpty()) {
            return null;
        }

        String apiKey;
        try {
            apiKey = readSecret(secretName);
        } catch (Exception e) {
            // The message names the secret, never its value.
            throw new IllegalStateException(String.format("Could not read the pandoc API key from the Polarion secret '%s'", secretName), e);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(String.format("The Polarion secret '%s', configured as the pandoc API key, is empty or does not exist", secretName));
        }
        // Returned as stored: a credential is not ours to reshape.
        return apiKey;
    }

    @VisibleForTesting
    protected @Nullable String readSecret(@NotNull String secretName) {
        return PolarionSecretsManager.getInstance().readSecret(secretName);
    }
}
