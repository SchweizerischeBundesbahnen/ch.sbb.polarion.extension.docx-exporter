package ch.sbb.polarion.extension.docx_exporter.rest.model.settings.templates;

import ch.sbb.polarion.extension.generic.settings.SettingsModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Base64;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplatesModel extends SettingsModel {
    public static final String TEMPLATE_ENTRY_NAME = "TEMPLATE";

    private byte[] template;

    @Override
    protected String serializeModelData() {
        return serializeEntry(TEMPLATE_ENTRY_NAME, template == null ? null : Base64.getEncoder().encodeToString(template));
    }

    @Override
    protected void deserializeModelData(String serializedString) {
        String content = deserializeEntry(TEMPLATE_ENTRY_NAME, serializedString);
        if (content != null) {
            this.template = Base64.getDecoder().decode(content);
        }
    }
}
