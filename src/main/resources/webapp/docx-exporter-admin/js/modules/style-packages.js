import ExtensionContext from '../../ui/generic/js/modules/ExtensionContext.js';
import ConfigurationsPane from '../../ui/generic/js/modules/ConfigurationsPane.js';
import CustomSelect from '../../ui/generic/js/modules/CustomSelect.js';
import StylePackageUtils from './style-package-utils.js';

const DEFAULT_SETTING_NAME = "Default";

const ctx = new ExtensionContext({
    extension: 'docx-exporter',
    setting: 'style-package',
    scopeFieldId: 'scope'
});

const conf = new ConfigurationsPane({
    ctx: ctx,
    label: 'style package',
    setConfigurationContentCallback: setStylePackage,
    newConfigurationCallback: newConfigurationCreated
});

ctx.onClick(
    'save-toolbar-button', saveStylePackage,
    'cancel-toolbar-button', ctx.cancelEdit,
    'default-toolbar-button', revertToDefault,
    'revisions-toolbar-button', ctx.toggleRevisions
);

ctx.onBlur(
    'style-package-weight', StylePackageUtils.adjustWeight
);

const ChildConfigurations = {
    localizationSelect: new CustomSelect({
        selectContainer: ctx.getElementById("localization-select"),
        label: ctx.getElementById("localization-select-label")
    }),
    templateSelect: new CustomSelect({
        selectContainer: ctx.getElementById("template-select"),
        label: ctx.getElementById("template-select-label")
    }),
    webhooksSelect: new CustomSelect({
        selectContainer: ctx.getElementById("webhooks-select")
    }),

    load: function () {
        ctx.getElementById("child-configs-load-error").style.display = "none";

        return new Promise((resolve) => {
            Promise.all([
                this.loadSettingNames("localization", this.localizationSelect),
                this.loadSettingNames("templates", this.templateSelect),
                this.loadSettingNames("webhooks", this.webhooksSelect)
            ]).then(() => {
                resolve();
            }).catch(() => {
                ctx.getElementById("child-configs-load-error").style.display = "block";
            });
        });
    },

    loadSettingNames: function(setting, select) {
        return new Promise((resolve, reject) => {
            ctx.callAsync({
                method: 'GET',
                url: `/polarion/${ctx.extension}/rest/internal/settings/${setting}/names?scope=${ctx.scope}`,
                contentType: 'application/json',
                onOk: (responseText) => {
                    let namesCount = 0;
                    for (let name of JSON.parse(responseText)) {
                        namesCount++;

                        const addedOption = select.addOption(name.name);
                        if (name.scope !== ctx.scope) {
                            addedOption.checkbox.classList.add('parent');
                            addedOption.label.classList.add('parent');
                        }
                    }
                    if (namesCount === 0) {
                        reject();
                    } else {
                        resolve();
                    }
                },
                onError: () => reject()
            });
        });
    }
}

const LinkRoles = {
    rolesSelect: new CustomSelect({
        selectContainer: ctx.getElementById("roles-select"),
        multiselect: true
    }),

    load: function () {
        ctx.getElementById("link-roles-load-error").style.display = "none";

        return new Promise((resolve, reject) => {
            ctx.callAsync({
                method: 'GET',
                url: `/polarion/${ctx.extension}/rest/internal/link-role-names?scope=${ctx.scope}`,
                contentType: 'application/json',
                onOk: (responseText) => {
                    for (let name of JSON.parse(responseText)) {
                        this.rolesSelect.addOption(name);
                    }
                    resolve();
                },
                onError: () => {
                    ctx.getElementById("link-roles-load-error").style.display = "block";
                    reject();
                }
            });
        });
    }
}

const RenderComments = {
    renderCommentsSelect: new CustomSelect({
        selectContainer: ctx.getElementById("render-comments-select")
    }),

    init: function () {
        this.renderCommentsSelect.addOption('OPEN', 'Open');
        this.renderCommentsSelect.addOption('ALL', 'All');
    }
}

const Languages = {
    languageSelect: new CustomSelect({
        selectContainer: ctx.getElementById("language-select")
    }),

    init: function () {
        this.languageSelect.addOption('de', 'Deutsch');
        this.languageSelect.addOption('fr', new DOMParser().parseFromString(`Fran&ccedil;ais`, 'text/html').body.textContent);
        this.languageSelect.addOption('it', 'Italiano');
    }
}

function saveStylePackage() {
    ctx.hideActionAlerts();

    ctx.callAsync({
        method: 'PUT',
        url: `/polarion/${ctx.extension}/rest/internal/settings/${ctx.setting}/names/${conf.getSelectedConfiguration()}/content?scope=${ctx.scope}`,
        contentType: 'application/json',
        body: JSON.stringify({
            'matchingQuery': ctx.getValueById('matching-query'),
            'weight': ctx.getValueById('style-package-weight'),
            'exposeSettings': ctx.getCheckboxValueById('exposeSettings'),
            'localization': ChildConfigurations.localizationSelect.getSelectedValue(),
            'template': ChildConfigurations.templateSelect.getSelectedValue(),
            'webhooks': ctx.getCheckboxValueById('webhooks-checkbox') ? ChildConfigurations.webhooksSelect.getSelectedValue() : null,
            'renderComments': ctx.getCheckboxValueById('render-comments') ? RenderComments.renderCommentsSelect.getSelectedValue() : null,
            'cutEmptyChapters': ctx.getCheckboxValueById('cut-empty-chapters'),
            'cutEmptyWorkitemAttributes': ctx.getCheckboxValueById('cut-empty-wi-attributes'),
            'cutLocalURLs': ctx.getCheckboxValueById('cut-urls'),
            'addToC': ctx.getCheckboxValueById('toc'),
            'specificChapters': ctx.getCheckboxValueById('specific-chapters') ? ctx.getValueById('chapters') : null,
            'language': ctx.getCheckboxValueById('localization') ? Languages.languageSelect.getSelectedValue() : null,
            'linkedWorkitemRoles': ctx.getCheckboxValueById('selected-roles') ? LinkRoles.rolesSelect.getSelectedValue() : null,
            'removalSelector': ctx.getValueById('removal-selector-input'),
        }),
        onOk: () => {
            ctx.showSaveSuccessAlert();
            ctx.setNewerVersionNotificationVisible(false);
            conf.loadConfigurationNames();
        },
        onError: () => ctx.showSaveErrorAlert()
    });
}

function revertToDefault() {
    if (confirm("Are you sure you want to return the default value?")) {
        ctx.setLoadingErrorNotificationVisible(false);
        ctx.hideActionAlerts();

        ctx.callAsync({
            method: 'GET',
            url: `/polarion/${ctx.extension}/rest/internal/settings/${ctx.setting}/default-content`,
            contentType: 'application/json',
            onOk: (responseText) => {
                ctx.showRevertedToDefaultAlert();
                setStylePackage(responseText);
            },
            onError: () => ctx.setLoadingErrorNotificationVisible(true)
        });
    }
}

function setStylePackage(content) {
    const stylePackage = JSON.parse(content);

    ctx.setValueById('matching-query', stylePackage.matchingQuery || "");
    ctx.setValueById('style-package-weight', stylePackage.weight);
    ctx.getElementById('matching-query-container').style.display = DEFAULT_SETTING_NAME === conf.getSelectedConfiguration() ? "none" : "flex";

    ctx.setCheckboxValueById('exposeSettings', stylePackage.exposeSettings);
    ChildConfigurations.localizationSelect.selectValue(ChildConfigurations.localizationSelect.containsOption(stylePackage.localization) ? stylePackage.localization : DEFAULT_SETTING_NAME);
    ChildConfigurations.templateSelect.selectValue(ChildConfigurations.templateSelect.containsOption(stylePackage.template) ? stylePackage.template : DEFAULT_SETTING_NAME);
    ctx.setCheckboxValueById('webhooks-checkbox', !!stylePackage.webhooks);
    ctx.getElementById('webhooks-checkbox').dispatchEvent(new Event('change'));
    ChildConfigurations.webhooksSelect.selectValue(ChildConfigurations.webhooksSelect.containsOption(stylePackage.webhooks) ? stylePackage.webhooks : DEFAULT_SETTING_NAME);

    ctx.setValueById('removal-selector-input', stylePackage.removalSelector || "");
    ctx.setCheckboxValueById('render-comments', !!stylePackage.renderComments);
    ctx.getElementById('render-comments').dispatchEvent(new Event('change'));
    RenderComments.renderCommentsSelect.selectValue(stylePackage.renderComments || 'OPEN');

    ctx.setCheckboxValueById('cut-empty-chapters', stylePackage.cutEmptyChapters);
    ctx.setCheckboxValueById('cut-empty-wi-attributes', stylePackage.cutEmptyWorkitemAttributes);
    ctx.setCheckboxValueById('cut-urls', stylePackage.cutLocalURLs);

    ctx.setCheckboxValueById('specific-chapters', !!stylePackage.specificChapters);
    ctx.getElementById('specific-chapters').dispatchEvent(new Event('change'));
    ctx.setValueById('chapters', stylePackage.specificChapters || "");

    ctx.setCheckboxValueById('localization', !!stylePackage.language);
    ctx.getElementById('localization').dispatchEvent(new Event('change'));
    Languages.languageSelect.selectValue(stylePackage.language);

    ctx.setCheckboxValueById('toc', stylePackage.addToC);

    const rolesProvided = stylePackage.linkedWorkitemRoles && stylePackage.linkedWorkitemRoles.length && stylePackage.linkedWorkitemRoles.length > 0;
    ctx.setCheckboxValueById('selected-roles', rolesProvided);
    ctx.getElementById('selected-roles').dispatchEvent(new Event('change'));
    LinkRoles.rolesSelect.selectMultipleValues(stylePackage.linkedWorkitemRoles);

    if (stylePackage.bundleTimestamp !== ctx.getValueById('bundle-timestamp')) {
        ctx.setNewerVersionNotificationVisible(true);
    }
}

function newConfigurationCreated() {
    ctx.setValueById('style-package-weight', 50);
}

RenderComments.init();
Languages.init();
Promise.all([
    LinkRoles.load(),
    ChildConfigurations.load()
]).then(() => {
    conf.loadConfigurationNames();
});
