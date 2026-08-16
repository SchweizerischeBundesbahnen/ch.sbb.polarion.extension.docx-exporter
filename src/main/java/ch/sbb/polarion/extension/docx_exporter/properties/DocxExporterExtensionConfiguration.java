package ch.sbb.polarion.extension.docx_exporter.properties;

import ch.sbb.polarion.extension.generic.properties.CurrentExtensionConfiguration;
import ch.sbb.polarion.extension.generic.properties.ExtensionConfiguration;
import ch.sbb.polarion.extension.generic.util.Discoverable;
import com.polarion.core.config.impl.SystemValueReader;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Discoverable
public class DocxExporterExtensionConfiguration extends ExtensionConfiguration {

    public static final String DEBUG_DESCRIPTION = "Enable <a href='#debug-option'>debug mode</a>";

    public static final String PANDOC_SERVICE = "pandoc.service";
    public static final String PANDOC_SERVICE_DEFAULT_VALUE = "http://localhost:9082";

    public static final String PANDOC_API_KEY_SECRET = "pandoc.apiKeySecret";
    public static final String PANDOC_API_KEY_SECRET_DESCRIPTION = "Name of the Polarion secret holding the <a href='#pandoc-api-key'>API key of the pandoc service</a>";
    public static final String PANDOC_API_KEY_SECRET_DEFAULT_VALUE = "";

    public static final String WEBHOOKS_ENABLED = "webhooks.enabled";
    public static final String WEBHOOKS_ENABLED_DESCRIPTION = "Enable <a href='#enabling-webhooks'>webhooks</a>";
    public static final Boolean WEBHOOKS_ENABLED_DEFAULT_VALUE = false;

    public static final String TEMPLATE_MAX_SIZE_MB = "templateMaxSizeMB";
    public static final String TEMPLATE_MAX_SIZE_MB_DESCRIPTION = "Size in MB an uploaded <a href='#reference-template-size-limit'>reference template</a> may reach";
    public static final int TEMPLATE_MAX_SIZE_MB_DEFAULT_VALUE = 16;

    public static final String EXTERNAL_RESOURCES_POLICY = "externalResources.policy";
    public static final String EXTERNAL_RESOURCES_POLICY_DESCRIPTION = "BLOCK_INTERNAL, ALLOWLIST_ONLY or ALLOW_ALL: where a document may load <a href='#external-resources'>images, fonts and stylesheets</a> from";
    public static final String EXTERNAL_RESOURCES_POLICY_DEFAULT_VALUE = "BLOCK_INTERNAL";

    public static final String EXTERNAL_RESOURCES_ALLOWED_ORIGINS = "externalResources.allowedOrigins";
    public static final String EXTERNAL_RESOURCES_ALLOWED_ORIGINS_DESCRIPTION = "Comma separated origins, [scheme://]host[:port], which are always allowed as a source of <a href='#external-resources'>external resources</a>";
    public static final String EXTERNAL_RESOURCES_ALLOWED_ORIGINS_DEFAULT_VALUE = "";

    public static final String EXTERNAL_RESOURCES_MAX_SIZE_MB = "externalResources.maxSizeMB";
    public static final String EXTERNAL_RESOURCES_MAX_SIZE_MB_DESCRIPTION = "Size in MB a single loaded <a href='#external-resources'>external resource</a> may reach";
    public static final int EXTERNAL_RESOURCES_MAX_SIZE_MB_DEFAULT_VALUE = 16;

    @Override
    public String getDebugDescription() {
        return DEBUG_DESCRIPTION;
    }

    public String getPandocService() {
        return SystemValueReader.getInstance().readString(getPropertyPrefix() + PANDOC_SERVICE, PANDOC_SERVICE_DEFAULT_VALUE);
    }

    public String getPandocApiKeySecret() {
        return SystemValueReader.getInstance().readString(getPropertyPrefix() + PANDOC_API_KEY_SECRET, PANDOC_API_KEY_SECRET_DEFAULT_VALUE);
    }

    @SuppressWarnings("unused")
    public String getPandocApiKeySecretDescription() {
        return PANDOC_API_KEY_SECRET_DESCRIPTION;
    }

    @SuppressWarnings("unused")
    public String getPandocApiKeySecretDefaultValue() {
        return PANDOC_API_KEY_SECRET_DEFAULT_VALUE;
    }

    @NotNull
    public Boolean getWebhooksEnabled() {
        return SystemValueReader.getInstance().readBoolean(getPropertyPrefix() + WEBHOOKS_ENABLED, WEBHOOKS_ENABLED_DEFAULT_VALUE);
    }

    @SuppressWarnings("unused")
    public String getWebhooksEnabledDescription() {
        return WEBHOOKS_ENABLED_DESCRIPTION;
    }

    @SuppressWarnings("unused")
    public String getWebhooksEnabledDefaultValue() {
        return String.valueOf(WEBHOOKS_ENABLED_DEFAULT_VALUE);
    }

    public int getTemplateMaxSizeMB() {
        return SystemValueReader.getInstance().readInt(getPropertyPrefix() + TEMPLATE_MAX_SIZE_MB, TEMPLATE_MAX_SIZE_MB_DEFAULT_VALUE);
    }

    @SuppressWarnings("unused")
    public String getTemplateMaxSizeMBDescription() {
        return TEMPLATE_MAX_SIZE_MB_DESCRIPTION;
    }

    @SuppressWarnings("unused")
    public String getTemplateMaxSizeMBDefaultValue() {
        return String.valueOf(TEMPLATE_MAX_SIZE_MB_DEFAULT_VALUE);
    }

    public String getExternalResourcesPolicy() {
        return SystemValueReader.getInstance().readString(getPropertyPrefix() + EXTERNAL_RESOURCES_POLICY, EXTERNAL_RESOURCES_POLICY_DEFAULT_VALUE);
    }

    @SuppressWarnings("unused")
    public String getExternalResourcesPolicyDescription() {
        return EXTERNAL_RESOURCES_POLICY_DESCRIPTION;
    }

    @SuppressWarnings("unused")
    public String getExternalResourcesPolicyDefaultValue() {
        return EXTERNAL_RESOURCES_POLICY_DEFAULT_VALUE;
    }

    public String getExternalResourcesAllowedOrigins() {
        return SystemValueReader.getInstance().readString(getPropertyPrefix() + EXTERNAL_RESOURCES_ALLOWED_ORIGINS, EXTERNAL_RESOURCES_ALLOWED_ORIGINS_DEFAULT_VALUE);
    }

    @SuppressWarnings("unused")
    public String getExternalResourcesAllowedOriginsDescription() {
        return EXTERNAL_RESOURCES_ALLOWED_ORIGINS_DESCRIPTION;
    }

    @SuppressWarnings("unused")
    public String getExternalResourcesAllowedOriginsDefaultValue() {
        return EXTERNAL_RESOURCES_ALLOWED_ORIGINS_DEFAULT_VALUE;
    }

    public int getExternalResourcesMaxSizeMB() {
        return SystemValueReader.getInstance().readInt(getPropertyPrefix() + EXTERNAL_RESOURCES_MAX_SIZE_MB, EXTERNAL_RESOURCES_MAX_SIZE_MB_DEFAULT_VALUE);
    }

    @SuppressWarnings("unused")
    public String getExternalResourcesMaxSizeMBDescription() {
        return EXTERNAL_RESOURCES_MAX_SIZE_MB_DESCRIPTION;
    }

    @SuppressWarnings("unused")
    public String getExternalResourcesMaxSizeMBDefaultValue() {
        return String.valueOf(EXTERNAL_RESOURCES_MAX_SIZE_MB_DEFAULT_VALUE);
    }

    @Override
    public @NotNull List<String> getSupportedProperties() {
        List<String> supportedProperties = new ArrayList<>(super.getSupportedProperties());
        supportedProperties.add(PANDOC_SERVICE);
        supportedProperties.add(PANDOC_API_KEY_SECRET);
        supportedProperties.add(TEMPLATE_MAX_SIZE_MB);
        supportedProperties.add(EXTERNAL_RESOURCES_POLICY);
        supportedProperties.add(EXTERNAL_RESOURCES_ALLOWED_ORIGINS);
        supportedProperties.add(EXTERNAL_RESOURCES_MAX_SIZE_MB);
        return supportedProperties;
    }

    public static DocxExporterExtensionConfiguration getInstance() {
        return (DocxExporterExtensionConfiguration) CurrentExtensionConfiguration.getInstance().getExtensionConfiguration();
    }
}
