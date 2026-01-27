import ExportParams from "./ExportParams.js";
import ExportContext from "./ExportContext.js";

export default class ExportPanel {

    constructor(rootComponentSelector) {
        this.ctx = new ExportContext({rootComponentSelector: rootComponentSelector});

        this.ctx.onChange('docx-style-package-select', () => {
            this.stylePackageChanged()
        });
        this.ctx.onClick('export-docx', () => {
            this.loadDocx()
        });
    }

    stylePackageChanged() {
        const selectedStylePackage = this.ctx.getElementById("docx-style-package-select").value;
        const scope = this.ctx.querySelector("input[name='scope']").value;
        if (selectedStylePackage && scope) {
            this.ctx.querySelectorAll('button').forEach(actionButton => {
                actionButton.disabled = true;
            });

            const $stylePackageError = this.ctx.getJQueryElement("#docx-style-package-error");
            $stylePackageError.empty();

            this.ctx.callAsync({
                method: "GET",
                url: `/polarion/docx-exporter/rest/internal/settings/style-package/names/${selectedStylePackage}/content?scope=${scope}`,
                responseType: "json",
                onOk: (responseText, request) => {
                    this.stylePackageSelected(request.response);
                    this.ctx.querySelectorAll('button').forEach(actionButton => {
                        actionButton.disabled = false;
                    });
                },
                onError: () => {
                    $stylePackageError.append("There was an error loading style package settings. Please, contact administrator");
                }
            });
        }
    }

    stylePackageSelected(stylePackage) {
        if (!stylePackage) {
            return;
        }
        const documentLanguage = this.ctx.getElementById("docx-document-language").value;

        this.ctx.setSelector("docx-template-selector", stylePackage.template);
        this.ctx.setSelector("docx-localization-selector", stylePackage.localization);
        this.ctx.setValue("docx-removal-selector", stylePackage.removalSelector);

        this.ctx.setCheckbox("docx-webhooks-checkbox", !!stylePackage.webhooks);
        this.ctx.setSelector("docx-webhooks-selector", stylePackage.webhooks);
        this.ctx.displayIf("docx-webhooks-selector", !!stylePackage.webhooks, "inline-block")

        this.ctx.setCheckbox("render-comments", !!stylePackage.renderComments);
        this.ctx.setValue("render-comments-selector", stylePackage.renderComments  || 'OPEN');
        this.ctx.displayIf("render-comments-selector", !!stylePackage.renderComments)

        this.ctx.setCheckbox("docx-cut-empty-chapters", stylePackage.cutEmptyChapters);
        this.ctx.setCheckbox("docx-cut-empty-wi-attributes", stylePackage.cutEmptyWorkitemAttributes);
        this.ctx.setCheckbox("docx-cut-urls", stylePackage.cutLocalURLs);

        this.ctx.setCheckbox("docx-specific-chapters", stylePackage.specificChapters);
        this.ctx.setValue("docx-chapters", stylePackage.specificChapters || "");
        this.ctx.displayIf("docx-chapters", stylePackage.specificChapters);

        this.ctx.setCheckbox("docx-localization", stylePackage.language);
        this.ctx.setValue("docx-language", (stylePackage.exposeSettings && stylePackage.language && documentLanguage) ? documentLanguage : stylePackage.language);
        this.ctx.displayIf("docx-language", stylePackage.language);

        this.ctx.setCheckbox("docx-orientation", !!stylePackage.orientation);
        this.ctx.setValue("docx-orientation-selector", stylePackage.orientation || ExportParams.Orientation.PORTRAIT);
        this.ctx.displayIf("docx-orientation-selector", !!stylePackage.orientation);

        this.ctx.setCheckbox("docx-paper-size", !!stylePackage.paperSize);
        this.ctx.setValue("docx-paper-size-selector", stylePackage.paperSize || ExportParams.PaperSize.A4);
        this.ctx.displayIf("docx-paper-size-selector", !!stylePackage.paperSize);

        const rolesProvided = stylePackage.linkedWorkitemRoles && stylePackage.linkedWorkitemRoles.length > 0;
        this.ctx.setCheckbox("docx-selected-roles", rolesProvided);
        this.ctx.querySelectorAll(`#docx-roles-selector option`).forEach(roleOption => {
            roleOption.selected = false;
        });
        if (rolesProvided) {
            for (const role of stylePackage.linkedWorkitemRoles) {
                this.ctx.querySelectorAll(`#docx-roles-selector option[value='${role}']`).forEach(roleOption => {
                    roleOption.selected = true;
                });
            }
        }
        this.ctx.displayIf("docx-roles-wrapper", rolesProvided);

        this.ctx.displayIf("docx-style-package-content", stylePackage.exposeSettings);
    }

    setClass(elementId, className) {
        this.ctx.getElementById(elementId).className = className;
    }

    prepareRequest(projectId, locationPath, baselineRevision, revision, fileName, targetFormat) {
        let selectedChapters = null;
        if (this.ctx.getElementById("docx-specific-chapters").checked) {
            selectedChapters = this.getSelectedChapters();
            this.setClass("docx-chapters", selectedChapters ? "" : "error");
            if (!selectedChapters) {
                this.ctx.getJQueryElement("#export-docx-error").append("Please, provide comma separated list of integer values in chapters field");
                return undefined;
            }
        }

        const selectedRoles = [];
        if (this.ctx.getElementById("docx-selected-roles").checked) {
            const selectedOptions = Array.from(this.ctx.getElementById("docx-roles-selector").options).filter(opt => opt.selected);
            selectedRoles.push(...selectedOptions.map(opt => opt.value));
        }

        return this.buildRequestJson(projectId, locationPath, baselineRevision, revision, selectedChapters, selectedRoles, fileName, targetFormat);
    }

    buildRequestJson(projectId, locationPath, baselineRevision, revision, selectedChapters, selectedRoles, fileName) {
        const urlSearchParams = new URL(window.location.href.replace('#', '/')).searchParams;
        return new ExportParams.Builder()
            .setProjectId(projectId)
            .setLocationPath(locationPath)
            .setBaselineRevision(baselineRevision)
            .setRevision(revision)
            .setTemplate(this.ctx.getElementById("docx-template-selector").value)
            .setLocalization(this.ctx.getElementById("docx-localization-selector").value)
            .setOrientation(this.ctx.getElementById("docx-orientation").checked ? this.ctx.getElementById("docx-orientation-selector").value : null)
            .setPaperSize(this.ctx.getElementById("docx-paper-size").checked ? this.ctx.getElementById("docx-paper-size-selector").value : null)
            .setWebhooks(this.ctx.getElementById("docx-webhooks-checkbox").checked ? this.ctx.getElementById("docx-webhooks-selector").value : null)
            .setRemovalSelector(this.ctx.getValueById("docx-removal-selector"))
            .setRenderComments(this.ctx.getElementById('render-comments').checked ? this.ctx.getElementById("render-comments-selector").value : null)
            .setCutEmptyChapters(this.ctx.getElementById("docx-cut-empty-chapters").checked)
            .setCutEmptyWIAttributes(this.ctx.getElementById('docx-cut-empty-wi-attributes').checked)
            .setCutLocalUrls(this.ctx.getElementById("docx-cut-urls").checked)
            .setChapters(selectedChapters)
            .setLanguage(this.ctx.getElementById('docx-localization').checked ? this.ctx.getElementById("docx-language").value : null)
            .setLinkedWorkitemRoles(selectedRoles)
            .setFileName(fileName)
            .setUrlQueryParameters(Object.fromEntries([...urlSearchParams]))
            .build()
            .toJSON();
    }

    getSelectedChapters() {
        const chaptersValue = this.ctx.getElementById("docx-chapters").value;
        let chapters = (chaptersValue?.replaceAll(" ", "") || "").split(",");
        if (chapters && chapters.length > 0) {
            for (const chapter of chapters) {
                const parsedValue = Number.parseInt(chapter);
                if (Number.isNaN(parsedValue) || parsedValue < 1 || String(parsedValue) !== chapter) {
                    // Stop processing if not valid numbers
                    return undefined;
                }
            }
        }
        return chapters;
    }

    loadDocx() {
        //clean previous errors
        this.ctx.getJQueryElement("#export-docx-error").empty();
        this.ctx.getJQueryElement("#export-docx-warning").empty();

        let fileName = this.ctx.getElementById("docx-filename").value;
        if (!fileName) {
            fileName = this.ctx.getElementById("docx-filename").dataset.default;
        }
        if (fileName && !fileName.endsWith(".docx")) {
            fileName += ".docx";
        }

        const projectId = this.ctx.getProjectId();
        const locationPath = this.ctx.getLocationPath();
        const baselineRevision = this.ctx.getBaselineRevision();
        const revision = this.ctx.getRevision();

        let request = this.prepareRequest(projectId, locationPath, baselineRevision, revision, fileName, "docx");
        if (request === undefined) {
            return;
        }

        this.actionInProgress(true);

        this.ctx.asyncConvertDoc(request, result => {
            if (result.warning) {
                this.ctx.getJQueryElement("#export-docx-warning").append(result.warning);
            }
            this.actionInProgress(false);

            this.ctx.downloadBlob(result.response, fileName);
        }, errorResponse => {
            this.actionInProgress(false);
            errorResponse.text().then(errorJson => {
                const error = errorJson && JSON.parse(errorJson);
                const errorMessage = error && (error.message ? error.message : error.errorMessage);
                this.ctx.getJQueryElement("#export-docx-error").append("Error occurred during DOCX generation" + (errorMessage ? ":<br>" + errorMessage : ""));
            });
        });
    }

    actionInProgress(inProgress) {
        if (inProgress) {
            //disable components
            this.ctx.getJQueryElement(":input").attr("disabled", true);
            //show loading icon
            this.ctx.getJQueryElement("#export-docx-progress").show();
        } else {
            //enable components
            this.ctx.getJQueryElement(":input").attr("disabled", false);
            //hide loading icon
            this.ctx.getJQueryElement("#export-docx-progress").hide();
        }
    }
}
