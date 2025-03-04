package ch.sbb.polarion.extension.docx_exporter.rest.model.settings.stylepackage;

import ch.sbb.polarion.extension.generic.settings.NamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingsModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StylePackageModel extends SettingsModel {

    public static final Float DEFAULT_WEIGHT = 50f;
    public static final Float DEFAULT_INITIAL_WEIGHT = 0f; // used to initialize the 'Default' settings in the default/repository scope

    private static final String MATCHING_QUERY_ENTRY_NAME = "MATCHING QUERY";
    private static final String WEIGHT_ENTRY_NAME = "WEIGHT";
    private static final String EXPOSE_SETTINGS_ENTRY_NAME = "EXPOSE SETTINGS";
    private static final String LOCALIZATION_ENTRY_NAME = "LOCALIZATION";
    private static final String WEBHOOKS_ENTRY_NAME = "WEBHOOKS";
    private static final String RENDER_COMMENTS_ENTRY_NAME = "RENDER COMMENTS";
    private static final String CUT_EMPTY_CHAPTERS_ENTRY_NAME = "CUT EMPTY CHAPTERS";
    private static final String CUT_EMPTY_WORKITEM_ATTRIBUTES_ENTRY_NAME = "CUT EMPTY WORKITEM ATTRIBUTES";
    private static final String CUT_LOCAL_URLS_ENTRY_NAME= "CUT LOCAL URLS";
    private static final String SPECIFIC_CHAPTERS_ENTRY_NAME = "SPECIFIC CHAPTERS";
    private static final String CUSTOM_NUMBERED_LIST_STYLES_ENTRY_NAME = "CUSTOM NUMBERED LIST STYLES";
    private static final String LANGUAGE_ENTRY_NAME = "LANGUAGE";
    private static final String LINKED_WORKITEM_ROLES_ENTRY_NAME = "LINKED WORKITEM ROLES";
    private static final String ATTACHMENTS_FILTER = "ATTACHMENTS_FILTER";

    private String matchingQuery;
    private Float weight;
    private boolean exposeSettings;
    private String localization;
    private String webhooks;
    private boolean renderComments;
    private boolean cutEmptyChapters;
    private boolean cutEmptyWorkitemAttributes;
    private boolean cutLocalURLs;
    private String specificChapters;
    private String language;
    private List<String> linkedWorkitemRoles;

    @Override
    protected String serializeModelData() {
        return serializeEntry(MATCHING_QUERY_ENTRY_NAME, matchingQuery) +
                serializeEntry(WEIGHT_ENTRY_NAME, weight) +
                serializeEntry(EXPOSE_SETTINGS_ENTRY_NAME, exposeSettings) +
                serializeEntry(LOCALIZATION_ENTRY_NAME, localization) +
                serializeEntry(WEBHOOKS_ENTRY_NAME, webhooks) +
                serializeEntry(RENDER_COMMENTS_ENTRY_NAME, renderComments) +
                serializeEntry(CUT_EMPTY_CHAPTERS_ENTRY_NAME, cutEmptyChapters) +
                serializeEntry(CUT_EMPTY_WORKITEM_ATTRIBUTES_ENTRY_NAME, cutEmptyWorkitemAttributes) +
                serializeEntry(CUT_LOCAL_URLS_ENTRY_NAME, cutLocalURLs) +
                serializeEntry(SPECIFIC_CHAPTERS_ENTRY_NAME, specificChapters) +
                serializeEntry(LANGUAGE_ENTRY_NAME, language) +
                serializeEntry(LINKED_WORKITEM_ROLES_ENTRY_NAME, linkedWorkitemRoles);
    }

    @Override
    protected void deserializeModelData(String serializedString) {
        matchingQuery = deserializeEntry(MATCHING_QUERY_ENTRY_NAME, serializedString);
        weight = Optional.ofNullable(deserializeEntry(WEIGHT_ENTRY_NAME, serializedString)).map(Float::parseFloat)
                .orElse(NamedSettings.DEFAULT_NAME.equals(name) ? DEFAULT_INITIAL_WEIGHT : DEFAULT_WEIGHT);
        exposeSettings = Boolean.parseBoolean(deserializeEntry(EXPOSE_SETTINGS_ENTRY_NAME, serializedString));
        localization = deserializeEntry(LOCALIZATION_ENTRY_NAME, serializedString);
        webhooks = deserializeEntry(WEBHOOKS_ENTRY_NAME, serializedString);
        renderComments = Boolean.parseBoolean(deserializeEntry(RENDER_COMMENTS_ENTRY_NAME, serializedString));
        cutEmptyChapters = Boolean.parseBoolean(deserializeEntry(CUT_EMPTY_CHAPTERS_ENTRY_NAME, serializedString));
        cutEmptyWorkitemAttributes = Boolean.parseBoolean(deserializeEntry(CUT_EMPTY_WORKITEM_ATTRIBUTES_ENTRY_NAME, serializedString));
        cutLocalURLs = Boolean.parseBoolean(deserializeEntry(CUT_LOCAL_URLS_ENTRY_NAME, serializedString));
        specificChapters = deserializeEntry(SPECIFIC_CHAPTERS_ENTRY_NAME, serializedString);
        language = deserializeEntry(LANGUAGE_ENTRY_NAME, serializedString);
        linkedWorkitemRoles = deserializeListEntry(LINKED_WORKITEM_ROLES_ENTRY_NAME, serializedString, String.class);
    }
}
