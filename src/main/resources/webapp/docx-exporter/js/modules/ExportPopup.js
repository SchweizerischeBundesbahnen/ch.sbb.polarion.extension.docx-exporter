import ExportParams from "./ExportParams.js";
import ExportContext from "./ExportContext.js";

export default class ExportPopup {

    constructor() {
        this.ctx = new ExportContext({rootComponentSelector: "#docx-export-popup"});
        this.initPopup();
    }

    initPopup() {
        const popup = document.createElement('div');
        popup.classList.add("modal");
        popup.classList.add("micromodal-slide");
        popup.id = POPUP_ID;
        popup.setAttribute("aria-hidden", "true");
        popup.innerHTML = POPUP_HTML;
        this.popup = popup;
        document.body.appendChild(popup);

        fetch('/polarion/docx-exporter/html/popupForm.html')
            .then(response => response.text())
            .then(content => {
                this.renderPanel(content);
            });
    }

    renderPanel(content) {
        this.ctx.querySelector(".modal__content").innerHTML = content;
        this.ctx.querySelector(".modal__footer .action-button").style.display = "inline-block";

        this.ctx.onChange('popup-docx-style-package-select', () => {
            this.onStylePackageChanged()
        });
        this.ctx.onClick('popup-export-docx', () => {
            this.exportToDocx()
        });

        this.openPopup();
    }

    openPopup() {

        this.hideAlerts();
        this.loadFormData();

        MicroModal.show(POPUP_ID);
    }

    loadFormData() {
        this.actionInProgress({inProgress: true, message: "Loading form data"});

        Promise.all([
            this.loadSettingNames({
                setting: "templates",
                scope: this.ctx.getScope(),
                selectElement: this.ctx.getElementById("popup-docx-template-selector")
            }),
            this.loadSettingNames({
                setting: "localization",
                scope: this.ctx.getScope(),
                selectElement: this.ctx.getElementById("popup-docx-localization-selector")
            }),
            this.loadSettingNames({
                setting: "webhooks",
                scope: this.ctx.getScope(),
                selectElement: this.ctx.getElementById("popup-docx-webhooks-selector")
            }),
            this.adjustWebhooksVisibility(),
            this.loadLinkRoles(),
            this.loadDocumentLanguage(),
            this.loadFileName(),
        ]).then(() => {
            return this.loadStylePackages();
        }).catch((error) => {
            this.showNotification({alertType: "error", message: "Error occurred loading form data" + (error.response?.message ? ": " + error.response.message : "")});
            this.actionInProgress({inProgress: false});
        });
    }

    loadSettingNames({setting, scope, selectElement, customUrl, customMethod, customBody, customContentType}) {
        return new Promise((resolve, reject) => {
            this.callAsync({
                method: customMethod ? customMethod : "GET",
                url: customUrl ? customUrl : `/polarion/docx-exporter/rest/internal/settings/${setting}/names?scope=${scope}`,
                body: customBody ? customBody : undefined,
                contentType: customContentType ? customContentType : undefined,
                responseType: "json",
            }).then(({response}) => {
                selectElement.innerHTML = ""; // Clear previously loaded content
                let namesCount = 0;
                for (let name of response) {
                    namesCount++;
                    const option = document.createElement('option');
                    option.value = name.name;
                    option.text = name.name;
                    selectElement.appendChild(option);
                }
                if (namesCount === 0) {
                    reject();
                } else {
                    resolve();
                }
            }).catch((error) => reject(error));
        });
    }

    loadLinkRoles() {
        return new Promise((resolve, reject) => {
            this.callAsync({
                method: "GET",
                url: `/polarion/docx-exporter/rest/internal/link-role-names?scope=${this.ctx.getScope()}`,
                responseType: "json",
            }).then(({response}) => {
                const selectElement = this.ctx.getElementById("popup-docx-roles-selector");
                selectElement.innerHTML = ""; // Clear previously loaded content
                for (let name of response) {
                    const option = document.createElement('option');
                    option.value = name;
                    option.text = name;
                    selectElement.appendChild(option);
                }
                resolve();
            }).catch((error) => reject(error));
        });
    }

    loadFileName() {
        const requestBody = this.ctx.toExportParams().toJSON();

        return new Promise((resolve, reject) => {
            this.callAsync({
                method: "POST",
                url: `/polarion/docx-exporter/rest/internal/export-filename`,
                body: requestBody,
            }).then(({responseText}) => {
                this.ctx.getElementById("popup-docx-filename").value = responseText;
                this.ctx.getElementById("popup-docx-filename").dataset.default = responseText;
                resolve()
            }).catch((error) => reject(error));
        });
    }

    adjustWebhooksVisibility() {
        return new Promise((resolve, reject) => {
            this.callAsync({
                method: "GET",
                url: `/polarion/docx-exporter/rest/internal/webhooks/status`,
                responseType: "json",
            }).then(({response}) => {
                this.ctx.getElementById("docx-webhooks-container").style.display = response.enabled ? "flex" : "none";
                resolve()
            }).catch((error) => reject(error));
        });
    }

    loadDocumentLanguage() {
        let url = `/polarion/docx-exporter/rest/internal/document-language?projectId=${this.ctx.getProjectId()}&spaceId=${this.ctx.getSpaceId()}&documentName=${this.ctx.getDocumentName()}`;
        if (this.ctx.revision) {
            url += `&revision=${this.ctx.revision}`;
        }
        return new Promise((resolve, reject) => {
            this.callAsync({
                method: "GET",
                url: url,
            }).then(({responseText}) => {
                this.documentLanguage = responseText;
                resolve();
            }).catch((error) => reject(error));
        });
    }

    loadStylePackages() {
        let stylePackagesUrl = `/polarion/docx-exporter/rest/internal/settings/style-package/suitable-names`;
        const docIdentifiers = [];
        docIdentifiers.push({
            projectId: `${this.ctx.getProjectId()}`, spaceId: `${this.ctx.getSpaceId()}`, documentName: `${this.ctx.getDocumentName()}`
        });

        return this.loadSettingNames({
            customUrl: stylePackagesUrl,
            selectElement: this.ctx.getElementById("popup-docx-style-package-select"),
            customMethod: 'POST',
            customBody: JSON.stringify(docIdentifiers),
            customContentType: 'application/json'
        }).then(() => {
            const stylePackageSelect = this.ctx.getElementById("popup-docx-style-package-select");
            const valueToPreselect = this.ctx.getCookie(SELECTED_STYLE_PACKAGE_COOKIE);
            if (valueToPreselect && this.ctx.containsOption(stylePackageSelect, valueToPreselect)) {
                stylePackageSelect.value = valueToPreselect;
            }

            this.onStylePackageChanged();
            this.actionInProgress({inProgress: false});
        });
    }

    onStylePackageChanged() {
        const selectedStylePackageName = this.ctx.getElementById("popup-docx-style-package-select").value;
        if (selectedStylePackageName) {
            this.ctx.setCookie(SELECTED_STYLE_PACKAGE_COOKIE, selectedStylePackageName);

            this.actionInProgress({inProgress: true, message: "Loading style package data"});

            this.callAsync({
                method: "GET",
                url: `/polarion/docx-exporter/rest/internal/settings/style-package/names/${selectedStylePackageName}/content?scope=${this.ctx.getScope()}`,
                responseType: "json",
            }).then(({response}) => {
                this.stylePackageSelected(response);

                this.actionInProgress({inProgress: false});
            }).catch((error) => {
                this.showNotification({alertType: "error", message: "Error occurred loading style package data" + (error?.response.message ? ": " + error.response.message : "")});
                this.actionInProgress({inProgress: false});
            });
        }
    }

    stylePackageSelected(stylePackage) {
        if (!stylePackage) {
            return;
        }
        this.ctx.setSelector("popup-docx-template-selector", stylePackage.template);
        this.ctx.setSelector("popup-docx-localization-selector", stylePackage.localization);
        this.ctx.setValue("popup-docx-removal-selector", stylePackage.removalSelector);

        this.ctx.setCheckbox("popup-docx-orientation", !!stylePackage.orientation);
        this.ctx.setValue("popup-docx-orientation-selector", stylePackage.orientation || ExportParams.Orientation.PORTRAIT);
        this.ctx.displayIf("popup-docx-orientation-selector", !!stylePackage.orientation);

        this.ctx.setCheckbox("popup-docx-paper-size", !!stylePackage.paperSize);
        this.ctx.setValue("popup-docx-paper-size-selector", stylePackage.paperSize || ExportParams.PaperSize.A4);
        this.ctx.displayIf("popup-docx-paper-size-selector", !!stylePackage.paperSize);

        this.ctx.setCheckbox("popup-docx-webhooks-checkbox", !!stylePackage.webhooks);
        this.ctx.setSelector("popup-docx-webhooks-selector", stylePackage.webhooks);
        this.ctx.visibleIf("popup-docx-webhooks-selector", !!stylePackage.webhooks)

        this.ctx.setCheckbox("popup-docx-render-comments", !!stylePackage.renderComments);
        this.ctx.setValue("popup-docx-render-comments-selector", stylePackage.renderComments || 'OPEN');
        this.ctx.visibleIf("popup-docx-render-comments-selector", !!stylePackage.renderComments);

        this.ctx.setCheckbox("popup-docx-cut-urls", stylePackage.cutLocalURLs);
        this.ctx.setCheckbox("popup-docx-cut-empty-chapters", stylePackage.cutEmptyChapters);
        this.ctx.setCheckbox("popup-docx-cut-empty-wi-attributes", stylePackage.cutEmptyWorkitemAttributes);

        this.ctx.setCheckbox("popup-docx-specific-chapters", stylePackage.specificChapters);
        this.ctx.setValue("popup-docx-chapters", stylePackage.specificChapters || "");
        this.ctx.visibleIf("popup-docx-chapters", stylePackage.specificChapters);

        this.ctx.setCheckbox("popup-docx-localization", stylePackage.language);
        let languageValue;
        if (stylePackage.exposeSettings && stylePackage.language && this.documentLanguage) {
            languageValue = this.documentLanguage;
        } else if (stylePackage.language) {
            languageValue = stylePackage.language;
        } else {
            const firstOption = this.ctx.getElementById("popup-docx-language").querySelector("option:first-child");
            languageValue = firstOption?.value;
        }
        this.ctx.setValue("popup-docx-language", languageValue);
        this.ctx.visibleIf("popup-docx-language", stylePackage.language);

        const rolesProvided = stylePackage.linkedWorkitemRoles && stylePackage.linkedWorkitemRoles.length && stylePackage.linkedWorkitemRoles.length > 0;
        this.ctx.setCheckbox("popup-docx-selected-roles", rolesProvided);
        this.ctx.querySelectorAll(`#popup-docx-roles-selector option`).forEach(roleOption => {
            roleOption.selected = false;
        });
        if (stylePackage.linkedWorkitemRoles) {
            for (const role of stylePackage.linkedWorkitemRoles) {
                this.ctx.querySelectorAll(`#popup-docx-roles-selector option[value='${role}']`).forEach(roleOption => {
                    roleOption.selected = true;
                });
            }
        }
        this.ctx.displayIf("popup-docx-roles-selector", rolesProvided, "inline-block");

        this.ctx.displayIf("popup-docx-style-package-content", stylePackage.exposeSettings);
    }

    exportToDocx() {
        this.hideAlerts();

        let fileName = this.ctx.getElementById("popup-docx-filename").value;
        if (!fileName) {
            fileName = this.ctx.getElementById("popup-docx-filename").dataset.default;
        }
        if (fileName && !fileName.endsWith(".docx")) {
            fileName += ".docx";
        }

        const exportParams = this.getExportParams(fileName);
        if (exportParams === undefined) {
            return;
        }

        this.actionInProgress({inProgress: true, message: "Generating DOCX"})
        const requestBody = exportParams.toJSON();

        this.ctx.asyncConvertDoc(requestBody, result => {
            if (result.warning) {
                this.showNotification({alertType: "warning", message: result.warning});
            }
            this.ctx.downloadBlob(result.response, fileName);

            this.showNotification({alertType: "success", message: "DOCX was successfully generated"});
            this.actionInProgress({inProgress: false});
        }, errorResponse => {
            errorResponse.text().then(errorJson => {
                const error = errorJson && JSON.parse(errorJson);
                const errorMessage = error && (error.message ? error.message : error.errorMessage);
                this.showNotification({alertType: "error", message: "Error occurred during DOCX generation" + (errorMessage ? ": " + errorMessage : "")});
            });
            this.actionInProgress({inProgress: false});
        });
    }

    getExportParams(fileName) {
        let selectedChapters = null;
        if (this.ctx.getElementById("popup-docx-specific-chapters").checked) {
            selectedChapters = this.getSelectedChapters();
            if (!selectedChapters) {
                this.ctx.getElementById("popup-docx-chapters").classList.add("error");
                this.showNotification({alertType: "error", message: "Please, provide comma separated list of integer values in 'Specific higher level chapters' field"});
                // Stop processing if not valid numbers
                return undefined;
            }
        }

        const selectedRoles = [];
        if (this.ctx.getElementById("popup-docx-selected-roles").checked) {
            const selectedOptions = Array.from(this.ctx.getElementById("popup-docx-roles-selector").options).filter(opt => opt.selected);
            selectedRoles.push(...selectedOptions.map(opt => opt.value));
        }

        return this.buildExportParams(selectedChapters, selectedRoles, fileName);
    }

    buildExportParams(selectedChapters, selectedRoles, fileName) {
        return new ExportParams.Builder()
            .setProjectId(this.ctx.getProjectId())
            .setLocationPath(this.ctx.getLocationPath())
            .setBaselineRevision(this.ctx.getBaselineRevision())
            .setRevision(this.ctx.getRevision())
            .setTemplate(this.ctx.getElementById("popup-docx-template-selector").value)
            .setLocalization(this.ctx.getElementById("popup-docx-localization-selector").value)
            .setOrientation(this.ctx.getElementById("popup-docx-orientation").checked ? this.ctx.getElementById("popup-docx-orientation-selector").value : null)
            .setPaperSize(this.ctx.getElementById("popup-docx-paper-size").checked ? this.ctx.getElementById("popup-docx-paper-size-selector").value : null)
            .setWebhooks(this.ctx.getElementById("popup-docx-webhooks-checkbox").checked ? this.ctx.getElementById("popup-docx-webhooks-selector").value : null)
            .setRemovalSelector(this.ctx.getValueById("popup-docx-removal-selector"))
            .setRenderComments(this.ctx.getElementById('popup-docx-render-comments').checked ? this.ctx.getElementById("popup-docx-render-comments-selector").value : null)
            .setCutEmptyChapters(this.ctx.getElementById("popup-docx-cut-empty-chapters").checked)
            .setCutEmptyWIAttributes(this.ctx.getElementById('popup-docx-cut-empty-wi-attributes').checked)
            .setCutLocalUrls(this.ctx.getElementById("popup-docx-cut-urls").checked)
            .setChapters(selectedChapters)
            .setLanguage(this.ctx.getElementById('popup-docx-localization').checked ? this.ctx.getElementById("popup-docx-language").value : null)
            .setLinkedWorkitemRoles(selectedRoles)
            .setFileName(fileName)
            .setUrlQueryParameters(this.ctx.getUrlQueryParameters())
            .build();
    }

    getSelectedChapters() {
        const chaptersValue = this.ctx.getElementById("popup-docx-chapters").value;
        let selectedChapters = (chaptersValue?.replaceAll(" ", "") || "").split(",");
        if (selectedChapters && selectedChapters.length > 0) {
            for (const chapter of selectedChapters) {
                const parsedValue = Number.parseInt(chapter);
                if (Number.isNaN(parsedValue) || parsedValue < 1 || String(parsedValue) !== chapter) {
                    // Stop processing if not valid numbers
                    return undefined;
                }
            }
        }
        return selectedChapters;
    }

    actionInProgress({inProgress, message}) {
        if (inProgress) {
            this.hideAlerts();
        }
        this.ctx.querySelectorAll(".action-button").forEach(button => {
            button.disabled = inProgress;
        });
        this.ctx.getElementById("docx-in-progress-message").innerHTML = message;
        if (inProgress) {
            this.ctx.querySelector(".docx-in-progress-overlay").classList.add("show");
        } else {
            this.ctx.querySelector(".docx-in-progress-overlay").classList.remove("show");
        }
    }

    showNotification({alertType, message}) {
        const alert = this.ctx.querySelector(`.docx-notifications .alert.alert-${alertType}`);
        if (alert) {
            alert.textContent = message; // to avoid XSS do not use innerHTML here because message may contain arbitrary error response data
            alert.style.display = "block";
        }
    }

    hideAlerts() {
        this.ctx.querySelectorAll(".alert").forEach(alert => {
            alert.style.display = "none";
        });
        this.ctx.querySelectorAll("input.error").forEach(input => {
            input.classList.remove("error");
        });
    }

    callAsync({method, url, contentType, responseType, body}) {
        return new Promise((resolve, reject) => {
            this.ctx.callAsync({
                method: method,
                url: url,
                contentType: contentType || 'application/json',
                responseType: responseType,
                body: body,
                onOk: (responseText, request) => {
                    resolve(responseType === "blob" || responseType === "json" ? {response: request.response} : {responseText: responseText});
                },
                onError: (status, errorMessage, request) => {
                    reject(request);
                }
            });
        });
    }

}

const SELECTED_STYLE_PACKAGE_COOKIE = 'selected-style-package';
const POPUP_ID = "docx-export-modal-popup";
const POPUP_HTML = `
    <div class="modal__overlay" tabindex="-1" data-micromodal-close>
        <div id="docx-export-popup" class="modal__container docx-exporter" role="dialog" aria-modal="true" aria-labelledby="docx-export-modal-popup-title">
            <header class="modal__header">
                <h2 class="modal__title" id="docx-export-modal-popup-title" style="display: flex; justify-content: space-between; width: 100%">
                    <span>Export to DOCX</span>
                    <i class="fa fa-times" aria-hidden="true" data-micromodal-close style="cursor: pointer"></i>
                </h2>
            </header>
            <main class="modal__content">
                <span style="color: red; font-style: italic;">DOCX exporter extension wasn't fully initialized. Please, contact system administrator</span>
            </main>
            <footer class="modal__footer">
                <button class="polarion-JSWizardButton" data-micromodal-close aria-label="Close this dialog window">Close</button>
                <button id="popup-export-docx" class="polarion-JSWizardButton-Primary action-button" style="display: none;">Export</button>
            </footer>
        </div>
    </div>
`;
