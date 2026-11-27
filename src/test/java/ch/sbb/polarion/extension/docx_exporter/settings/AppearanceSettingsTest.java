package ch.sbb.polarion.extension.docx_exporter.settings;

import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.appearance.AppearanceModel;
import ch.sbb.polarion.extension.generic.context.CurrentContextConfig;
import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith({MockitoExtension.class, CurrentContextExtension.class})
@CurrentContextConfig("docx-exporter")
class AppearanceSettingsTest {

    @Test
    void featureNameIsAppearance() {
        assertThat(new AppearanceSettings(mock(SettingsService.class)).getFeatureName()).isEqualTo("appearance");
    }

    @Test
    void defaultValuesShowTheButton() {
        AppearanceModel defaults = new AppearanceSettings(mock(SettingsService.class)).defaultValues();
        assertThat(defaults.isLiveDocButton()).isTrue();
    }

    @Test
    void theFlagSurvivesSerialization() {
        AppearanceModel model = AppearanceModel.builder().liveDocButton(false).build();

        AppearanceModel read = new AppearanceModel();
        read.deserialize(model.serialize());

        assertThat(read.isLiveDocButton()).isFalse();
    }

    @Test
    void aSettingWithoutTheEntryKeepsTheButtonItHadBefore() {
        // A setting stored by an older version carries no entry. Reading it as "button off" would take the
        // button away from an installation which only upgraded.
        AppearanceModel read = new AppearanceModel();
        read.deserialize("-----BEGIN NAME-----\nDefault\n-----END NAME-----\n");

        assertThat(read.isLiveDocButton()).isTrue();
    }
}
