package ch.sbb.polarion.extension.docx_exporter;

import ch.sbb.polarion.extension.docx_exporter.settings.TemplatesSettings;
import ch.sbb.polarion.extension.generic.settings.NamedSettings;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingName;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import ch.sbb.polarion.extension.docx_exporter.properties.DocxExporterExtensionConfiguration;
import ch.sbb.polarion.extension.docx_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.localization.Language;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.stylepackage.DocIdentifier;
import ch.sbb.polarion.extension.docx_exporter.rest.model.settings.stylepackage.StylePackageModel;
import ch.sbb.polarion.extension.docx_exporter.service.DocxExporterPolarionService;
import ch.sbb.polarion.extension.docx_exporter.service.PolarionBaselineExecutor;
import ch.sbb.polarion.extension.docx_exporter.settings.LocalizationSettings;
import ch.sbb.polarion.extension.docx_exporter.settings.StylePackageSettings;
import ch.sbb.polarion.extension.docx_exporter.settings.WebhooksSettings;
import ch.sbb.polarion.extension.docx_exporter.util.DocumentFileNameHelper;
import ch.sbb.polarion.extension.docx_exporter.util.EnumValuesProvider;
import ch.sbb.polarion.extension.docx_exporter.util.ExceptionHandler;
import com.polarion.alm.shared.api.SharedContext;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.ui.server.forms.extensions.IFormExtension;
import com.polarion.alm.ui.server.forms.extensions.IFormExtensionContext;
import com.polarion.alm.ui.shared.CollectionUtils;
import com.polarion.core.util.StringUtils;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.model.IPObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ch.sbb.polarion.extension.docx_exporter.util.placeholder.PlaceholderValues.DOC_LANGUAGE_FIELD;

public class DocxExporterFormExtension implements IFormExtension {

    private static final String OPTION_TEMPLATE = "<option value='%s' %s>%s</option>";
    private static final String OPTION_VALUE = "<option value='%s'";
    private static final String OPTION_SELECTED = "<option value='%s' selected";
    private static final String SELECTED = "selected";

    private final DocxExporterPolarionService polarionService = new DocxExporterPolarionService();

    @Override
    @Nullable
    public String render(@NotNull IFormExtensionContext context) {
        return TransactionalExecutor.executeSafelyInReadOnlyTransaction(transaction -> {
            String baselineRevision = transaction.context().baselineRevision();
            return PolarionBaselineExecutor.executeInBaseline(baselineRevision, transaction, () -> renderForm(transaction.context(), context.object().getOldApi()));
        });
    }

    public String renderForm(@NotNull SharedContext context, @NotNull IPObject object) {
        HtmlFragmentBuilder builder = context.createHtmlFragmentBuilderFor().gwt();

        if (object instanceof IModule module) {

            String form = ScopeUtils.getFileContent("webapp/docx-exporter/html/sidePanelContent.html");

            String scope = ScopeUtils.getScopeFromProject(module.getProject().getId());
            form = form.replace("{SCOPE_VALUE}", scope);

            Collection<SettingName> stylePackageNames = getSuitableStylePackages(module);
            SettingName stylePackageNameToSelect = getStylePackageNameToSelect(stylePackageNames); // Either default (if exists) or first from list

            form = form.replace("{STYLE_PACKAGE_OPTIONS}", generateSelectOptions(stylePackageNames, stylePackageNameToSelect != null ? stylePackageNameToSelect.getName() : null));

            StylePackageModel selectedStylePackage = getSelectedStylePackage(stylePackageNameToSelect, scope); // Implicitly default one will be returned if no style package persisted (whatever the reason)

            if (!selectedStylePackage.isExposeSettings()) {
                form = form.replace("<div id='docx-style-package-content'", "<div id='docx-style-package-content' class='hidden'"); // Hide settings pane if style package settings not exposed to end users
            }

            form = adjustTemplate(scope, form, selectedStylePackage);
            form = adjustLocalization(scope, form, selectedStylePackage);
            form = adjustOrientation(form, selectedStylePackage);
            form = adjustPaperSize(form, selectedStylePackage);
            form = adjustWebhooks(scope, form, selectedStylePackage);
            form = adjustRenderComments(form, selectedStylePackage);
            form = adjustCutEmptyChapters(form, selectedStylePackage);
            form = adjustCutEmptyWorkitemAttributes(form, selectedStylePackage);
            form = adjustCutLocalURLs(form, selectedStylePackage);
            form = adjustChapters(form, selectedStylePackage);
            form = adjustLocalizeEnums(form, selectedStylePackage, module.getCustomField(DOC_LANGUAGE_FIELD));
            form = adjustLinkRoles(form, EnumValuesProvider.getAllLinkRoleNames(module.getProject()), selectedStylePackage);
            form = adjustRemovalSelector(form, selectedStylePackage);
            form = adjustFilename(form, module);
            form = adjustToC(form, selectedStylePackage);

            builder.html(form);
        }

        builder.finished();
        return builder.toString();
    }

    private SettingName getStylePackageNameToSelect(Collection<SettingName> stylePackageNames) {
        return stylePackageNames.stream()
                .findFirst()
                .orElse(null);
    }

    @VisibleForTesting
    StylePackageModel getSelectedStylePackage(SettingName defaultStylePackageName, String scope) {
        StylePackageSettings stylePackageSettings = (StylePackageSettings) NamedSettingsRegistry.INSTANCE.getByFeatureName(StylePackageSettings.FEATURE_NAME);
        return defaultStylePackageName != null
                ? stylePackageSettings.read(scope, SettingId.fromName(defaultStylePackageName.getName()), null)
                : stylePackageSettings.defaultValues();
    }

    private String adjustTemplate(String scope, String form, StylePackageModel stylePackage) {
        Collection<SettingName> templateNames = getSettingNames(TemplatesSettings.FEATURE_NAME, scope);
        String templateOptions = generateSelectOptions(templateNames, stylePackage.getTemplate());
        return form.replace("{TEMPLATE_OPTIONS}", templateOptions);
    }

    private String adjustLocalization(String scope, String form, StylePackageModel stylePackage) {
        Collection<SettingName> localizationNames = getSettingNames(LocalizationSettings.FEATURE_NAME, scope);
        String localizationOptions = generateSelectOptions(localizationNames, stylePackage.getLocalization());
        return form.replace("{LOCALIZATION_OPTIONS}", localizationOptions);
    }

    private String adjustWebhooks(String scope, String form, StylePackageModel stylePackage) {
        Collection<SettingName> webhooksNames = getSettingNames(WebhooksSettings.FEATURE_NAME, scope);
        boolean noHooks = StringUtils.isEmpty(stylePackage.getWebhooks());
        String webhooksOptions = generateSelectOptions(webhooksNames, noHooks ? NamedSettings.DEFAULT_NAME : stylePackage.getWebhooks());
        form = form.replace("{WEBHOOKS_DISPLAY}", DocxExporterExtensionConfiguration.getInstance().getWebhooksEnabled() ? "" : "hidden");
        form = form.replace("{WEBHOOKS_OPTIONS}", webhooksOptions);
        form = form.replace("{WEBHOOKS_SELECTOR_DISPLAY}", noHooks ? "none" : "inline-block");
        return form.replace("{WEBHOOKS_SELECTED}", noHooks ? "" : "checked");
    }

    @VisibleForTesting
    Collection<SettingName> getSuitableStylePackages(@NotNull IModule module) {
        String locationPath = module.getModuleLocation().getLocationPath();
        String spaceId = "";
        final String documentName;
        if (locationPath.contains("/")) {
            spaceId = locationPath.substring(0, locationPath.lastIndexOf('/'));
            documentName = locationPath.substring(locationPath.lastIndexOf('/') + 1);
        } else {
            documentName = locationPath;
        }
        return polarionService.getSuitableStylePackages(List.of(new DocIdentifier(module.getProject().getId(), spaceId, documentName)));
    }

    @VisibleForTesting
    @SuppressWarnings("unchecked")
    Collection<SettingName> getSettingNames(@NotNull String featureName, @NotNull String scope) {
        try {
            return NamedSettingsRegistry.INSTANCE.getByFeatureName(featureName).readNames(scope);
        } catch (IllegalStateException ex) {
            return ExceptionHandler.handleTransactionIllegalStateException(ex, Collections.emptyList());
        }
    }

    private String generateSelectOptions(Collection<SettingName> settingNames, String defaultName) {
        if (!settingNames.isEmpty()) {
             final String nameToPreselect;
             if (defaultName != null && settingNames.stream().map(SettingName::getName).anyMatch(name -> name.equals(defaultName))) {
                 nameToPreselect = defaultName;
             } else {
                 nameToPreselect = NamedSettings.DEFAULT_NAME;
             }

            return settingNames.stream()
                    .map(settingName -> String.format(OPTION_TEMPLATE,
                            settingName.getName(), settingName.getName().equals(nameToPreselect) ? SELECTED : "", settingName.getName()))
                    .collect(Collectors.joining());
        } else {
            return String.format(OPTION_TEMPLATE, NamedSettings.DEFAULT_NAME, SELECTED, NamedSettings.DEFAULT_NAME);
        }
    }

    private String adjustRenderComments(String form, StylePackageModel stylePackage) {
        if (stylePackage.getRenderComments() != null) {
            form = form.replace("<input id='render-comments'", "<input id='render-comments' checked");
            form = form.replace("id='render-comments-selector' style='display: none'", "id='render-comments-selector'");
            form = form.replace(String.format(OPTION_VALUE, stylePackage.getRenderComments()), String.format(OPTION_SELECTED, stylePackage.getRenderComments()));
        }
        return form;
    }

    private String adjustOrientation(String form, StylePackageModel stylePackage) {
        if (stylePackage.getOrientation() != null) {
            form = form.replace("<input id='docx-orientation'", "<input id='docx-orientation' checked");
            form = form.replace("id='docx-orientation-selector' style='display: none'", "id='docx-orientation-selector'");
            form = form.replace(String.format(OPTION_VALUE, stylePackage.getOrientation()), String.format(OPTION_SELECTED, stylePackage.getOrientation()));
        }
        return form;
    }

    private String adjustPaperSize(String form, StylePackageModel stylePackage) {
        if (stylePackage.getPaperSize() != null) {
            form = form.replace("<input id='docx-paper-size'", "<input id='docx-paper-size' checked");
            form = form.replace("id='docx-paper-size-selector' style='display: none'", "id='docx-paper-size-selector'");
            form = form.replace(String.format(OPTION_VALUE, stylePackage.getPaperSize()), String.format(OPTION_SELECTED, stylePackage.getPaperSize()));
        }
        return form;
    }

    private String adjustCutEmptyChapters(String form, StylePackageModel stylePackage) {
        return stylePackage.isCutEmptyChapters() ? form.replace("<input id='docx-cut-empty-chapters'", "<input id='docx-cut-empty-chapters' checked") : form;
    }

    private String adjustCutEmptyWorkitemAttributes(String form, StylePackageModel stylePackage) {
        return stylePackage.isCutEmptyWorkitemAttributes() ? form.replace("<input id='docx-cut-empty-wi-attributes'", "<input id='docx-cut-empty-wi-attributes' checked") : form;
    }

    private String adjustCutLocalURLs(String form, StylePackageModel stylePackage) {
        return stylePackage.isCutLocalURLs() ? form.replace("<input id='docx-cut-urls'", "<input id='docx-cut-urls' checked") : form;
    }

    private String adjustToC(String form, StylePackageModel stylePackage) {
        return stylePackage.isAddToC() ? form.replace("<input id='docx-toc'", "<input id='docx-toc' checked") : form;
    }

    private String adjustChapters(String form, StylePackageModel stylePackage) {
        if (!StringUtils.isEmpty(stylePackage.getSpecificChapters())) {
            form = form.replace("<input id='docx-specific-chapters'", "<input id='docx-specific-chapters' checked");
            form = form.replace("<input id='docx-chapters' style='display: none;", String.format("<input id='docx-chapters' value='%s' style='", stylePackage.getSpecificChapters()));
        }
        return form;
    }

    private String adjustLocalizeEnums(String form, StylePackageModel stylePackage, Object documentLanguageField) {
        String documentLanguage = (documentLanguageField instanceof IEnumOption enumOption) ? enumOption.getId() : "";

        if (!StringUtils.isEmpty(stylePackage.getLanguage())) {
            form = form.replace("<input id='docx-localization'", "<input id='docx-localization' checked");
            form = form.replace("id='docx-language' style='display: none'", "id='docx-language'");

            String languageToPreselect = null;
            for (Language language : Language.values()) {
                if (language.name().equalsIgnoreCase(documentLanguage) || language.getValue().equalsIgnoreCase(documentLanguage)) {
                    languageToPreselect = language.name().toLowerCase();
                    break;
                }
            }
            if (!stylePackage.isExposeSettings() || StringUtils.isEmpty(languageToPreselect)) {
                languageToPreselect = stylePackage.getLanguage();
            }

            form = form.replace(String.format(OPTION_VALUE, languageToPreselect), String.format(OPTION_SELECTED, languageToPreselect));
        }
        return form.replace("{DOCUMENT_LANGUAGE}", documentLanguage);
    }

    private String adjustLinkRoles(@NotNull String form, @NotNull List<String> roleEnumValues, @NotNull StylePackageModel stylePackage) {
        if (!roleEnumValues.isEmpty()) {
            if (!CollectionUtils.isEmpty(stylePackage.getLinkedWorkitemRoles())) {
                form = form.replace("<input id='docx-selected-roles'", "<input id='docx-selected-roles' checked");
                form = form.replace("id='docx-roles-wrapper' style='display: none'", "id='docx-roles-wrapper'");
            }

            String rolesOptions = roleEnumValues.stream()
                    .map(roleEnumValue -> String.format(OPTION_TEMPLATE,
                            roleEnumValue,
                            !CollectionUtils.isEmpty(stylePackage.getLinkedWorkitemRoles()) && stylePackage.getLinkedWorkitemRoles().contains(roleEnumValue) ? SELECTED : "",
                            roleEnumValue)
                    ).collect(Collectors.joining());
            return form.replace("{ROLES_OPTIONS}", rolesOptions);
        } else {
            return form.replace("class='docx-roles-fields'", "class='docx-roles-fields' style='display: none;'"); // Hide roles fields when no roles obtained
        }
    }

    private String adjustRemovalSelector(@NotNull String form, @NotNull StylePackageModel stylePackage) {
        return form.replace("{REMOVAL_SELECTOR}", StringUtils.getEmptyIfNull(stylePackage.getRemovalSelector()));
    }

    private String adjustFilename(@NotNull String form, @NotNull IModule module) {
        String filename = getFilename(module);
        return form.replace("{FILENAME}", filename).replace("{DATA_FILENAME}", filename);
    }

    @VisibleForTesting
    @SuppressWarnings("java:S3252") // allow to build ExportParams using its own builder
    String getFilename(@NotNull IModule module) {
        DocumentFileNameHelper documentFileNameHelper = new DocumentFileNameHelper();

        ExportParams exportParams = ExportParams.builder()
                .projectId(module.getProject().getId())
                .locationPath(module.getModuleLocation().getLocationPath())
                .revision(module.getRevision())
                .build();

        return documentFileNameHelper.getDocumentFileName(exportParams);
    }

    @Override
    @Nullable
    public String getIcon(@NotNull IPObject object, @Nullable Map<String, String> attributes) {
        return null;
    }

    @Override
    @Nullable
    public String getLabel(@NotNull IPObject object, @Nullable Map<String, String> attributes) {
        return "DOCX Exporter";
    }

}
