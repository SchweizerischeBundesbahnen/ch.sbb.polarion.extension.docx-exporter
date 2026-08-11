package ch.sbb.polarion.extension.docx_exporter.settings;

import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.templates.TemplatesModel;
import ch.sbb.polarion.extension.generic.settings.GenericNamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Arrays;

public class TemplatesSettings extends GenericNamedSettings<TemplatesModel> {
    public static final String FEATURE_NAME = "templates";

    /**
     * The first four bytes of every zip container, and a DOCX is one. Checking them keeps a file that is
     * plainly not a Word document out of the setting, without opening the archive: the Templates page
     * only offers the file picker its {@code .docx} filter, which the REST API does not have to honour.
     */
    private static final byte[] ZIP_SIGNATURE = {'P', 'K', 3, 4};

    public TemplatesSettings() {
        super(FEATURE_NAME);
    }

    public TemplatesSettings(SettingsService settingsService) {
        super(FEATURE_NAME, settingsService);
    }

    @Override
    public void beforeSave(@NotNull TemplatesModel what) {
        validateTemplate(what);
    }

    @Override
    public @NotNull TemplatesModel defaultValues() {
        return TemplatesModel.builder().build();
    }

    /**
     * Any authenticated user reaches this through the settings REST API, so the file an upload may store
     * is bounded by the {@code templateMaxSizeMB} property. No template at all is the default value of
     * this setting, so a missing one passes.
     */
    @VisibleForTesting
    void validateTemplate(@NotNull TemplatesModel model) {
        byte[] template = model.getTemplate();
        if (template == null) {
            return;
        }
        int maxSizeMB = DocxExporterExtensionConfiguration.getInstance().getTemplateMaxSizeMB();
        if (template.length > (long) maxSizeMB * 1024 * 1024) {
            throw new IllegalArgumentException("Template file must not exceed " + maxSizeMB + " MB");
        }
        if (!startsWithZipSignature(template)) {
            throw new IllegalArgumentException("Uploaded file must be a valid docx file");
        }
    }

    private static boolean startsWithZipSignature(byte @NotNull [] template) {
        return template.length >= ZIP_SIGNATURE.length
                && Arrays.equals(template, 0, ZIP_SIGNATURE.length, ZIP_SIGNATURE, 0, ZIP_SIGNATURE.length);
    }
}
