package ch.sbb.polarion.extension.docx_exporter.settings;

import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.templates.TemplatesModel;
import ch.sbb.polarion.extension.generic.settings.GenericNamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import org.jetbrains.annotations.NotNull;

public class TemplatesSettings extends GenericNamedSettings<TemplatesModel> {
    public static final String FEATURE_NAME = "templates";

    public TemplatesSettings() {
        super(FEATURE_NAME);
    }

    public TemplatesSettings(SettingsService settingsService) {
        super(FEATURE_NAME, settingsService);
    }

    @Override
    public @NotNull TemplatesModel defaultValues() {
        return TemplatesModel.builder().build();
    }
}
