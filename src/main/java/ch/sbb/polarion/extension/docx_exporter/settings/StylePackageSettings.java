package ch.sbb.polarion.extension.docx_exporter.settings;

import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.CommentsRenderType;
import ch.sbb.polarion.extension.generic.settings.GenericNamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.stylepackage.StylePackageModel;
import com.polarion.core.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public class StylePackageSettings extends GenericNamedSettings<StylePackageModel> {
    public static final String FEATURE_NAME = "style-package";

    public StylePackageSettings() {
        super(FEATURE_NAME);
    }

    public StylePackageSettings(SettingsService settingsService) {
        super(FEATURE_NAME, settingsService);
    }

    @Override
    public void beforeSave(@NotNull StylePackageModel what) {
        adjustAndValidateWeight(what);
        validateMatchingQuery(what);
    }

    @Override
    public @NotNull StylePackageModel defaultValues() {
        return StylePackageModel.builder()
                .localization(DEFAULT_NAME)
                .webhooks(DEFAULT_NAME)
                .renderComments(CommentsRenderType.OPEN)
                .cutEmptyWorkitemAttributes(true)
                .weight(StylePackageModel.DEFAULT_WEIGHT)
                .build();
    }

    @VisibleForTesting
    void adjustAndValidateWeight(StylePackageModel model) {
        Float weight = model.getWeight();
        if (weight == null) {
            model.setWeight(DEFAULT_NAME.equals(model.getName()) ? StylePackageModel.DEFAULT_INITIAL_WEIGHT : StylePackageModel.DEFAULT_WEIGHT);
        } else if (weight < 0 || weight > 100 || Math.abs(weight * 10 - Math.floor(weight * 10)) > 0) {
            throw new IllegalArgumentException("Weight must be between 0 and 100 and have only one digit after the decimal point");
        }
    }

    @VisibleForTesting
    void validateMatchingQuery(StylePackageModel model) {
        if (DEFAULT_NAME.equals(model.getName()) && !StringUtils.isEmpty(model.getMatchingQuery())) {
            throw new IllegalArgumentException("Matching query cannot be specified for a default style package");
        }
    }
}
