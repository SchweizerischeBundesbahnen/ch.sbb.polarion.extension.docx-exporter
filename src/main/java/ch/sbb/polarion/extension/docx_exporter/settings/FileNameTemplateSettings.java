package ch.sbb.polarion.extension.docx_exporter.settings;

import ch.sbb.polarion.extension.generic.settings.GenericNamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.filename.FileNameTemplateModel;
import org.jetbrains.annotations.NotNull;

public class FileNameTemplateSettings extends GenericNamedSettings<FileNameTemplateModel> {
    public static final String FEATURE_NAME = "filename-template";
    public static final String DEFAULT_LIVE_DOC_NAME_TEMPLATE = "$projectName $document.moduleNameWithSpace.replace(\" / \", \" \")";

    public FileNameTemplateSettings() {
        super(FEATURE_NAME);
    }

    public FileNameTemplateSettings(SettingsService settingsService) {
        super(FEATURE_NAME, settingsService);
    }

    @Override
    public @NotNull FileNameTemplateModel defaultValues() {
        return FileNameTemplateModel.builder()
                .documentNameTemplate(DEFAULT_LIVE_DOC_NAME_TEMPLATE)
                .build();
    }
}
