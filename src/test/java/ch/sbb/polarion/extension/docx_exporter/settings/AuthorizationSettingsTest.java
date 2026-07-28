package ch.sbb.polarion.extension.docx_exporter.settings;

import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.authorization.AuthorizationModel;
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
class AuthorizationSettingsTest {

    @Test
    void featureNameIsAuthorization() {
        assertThat(new AuthorizationSettings(mock(SettingsService.class)).getFeatureName()).isEqualTo("authorization");
    }

    @Test
    void defaultValuesAreUnrestricted() {
        AuthorizationModel defaults = new AuthorizationSettings(mock(SettingsService.class)).defaultValues();
        assertThat(defaults.getGlobalRoles()).isEmpty();
        assertThat(defaults.getProjectRoles()).isEmpty();
        assertThat(defaults.isUnrestricted()).isTrue();
    }
}
