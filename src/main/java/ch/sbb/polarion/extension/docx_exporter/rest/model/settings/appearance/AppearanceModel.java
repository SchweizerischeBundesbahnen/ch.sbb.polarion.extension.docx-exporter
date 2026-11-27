package ch.sbb.polarion.extension.docx_exporter.rest.model.settings.appearance;

import ch.sbb.polarion.extension.generic.settings.SettingsModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.polarion.core.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Which "Export to DOCX" buttons the extension puts on a Polarion page, per scope.
 *
 * A setting stored before this feature carries none of these entries, and an installation which never
 * opened the page has no setting at all. Both must keep the button which was there before, so every
 * missing entry falls back to the built-in default rather than to false.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppearanceModel extends SettingsModel {
    public static final boolean DEFAULT_DISPLAY = true;

    private static final String LIVE_DOC_BUTTON = "LIVE DOC BUTTON";

    /** The button in the document editor toolbar. */
    private boolean liveDocButton;

    @Override
    protected String serializeModelData() {
        return serializeEntry(LIVE_DOC_BUTTON, liveDocButton);
    }

    @Override
    protected void deserializeModelData(String serializedString) {
        liveDocButton = deserializeFlag(LIVE_DOC_BUTTON, serializedString, DEFAULT_DISPLAY);
    }

    private boolean deserializeFlag(String entryName, String serializedString, boolean defaultValue) {
        String value = deserializeEntry(entryName, serializedString);
        return StringUtils.isEmptyTrimmed(value) ? defaultValue : Boolean.parseBoolean(value);
    }
}
