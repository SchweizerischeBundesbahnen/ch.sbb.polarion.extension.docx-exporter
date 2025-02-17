const ExportDocx = {
    stylePackageChanged: function () {
        // const selectedStylePackage = document.getElementById(".docx-exporter #style-package-select").value;
        const selectedStylePackage = DocxExportCommon.getElementById("style-package-select").value;
        const scope = DocxExportCommon.querySelector("input[name='scope']").value;
        if (selectedStylePackage && scope) {
            DocxExportCommon.querySelectorAll('button').forEach(actionButton => {
                actionButton.disabled = true;
            });

            const $stylePackageError = DocxExportCommon.getJQueryElement("#style-package-error");
            $stylePackageError.empty();

            SbbCommon.callAsync({
                method: "GET",
                url: `/polarion/docx-exporter/rest/internal/settings/style-package/names/${selectedStylePackage}/content?scope=${scope}`,
                responseType: "json",
                onOk: (responseText, request) => {
                    ExportDocx.stylePackageSelected(request.response);
                    DocxExportCommon.querySelectorAll('button').forEach(actionButton => {
                        actionButton.disabled = false;
                    });
                },
                onError: () => {
                    $stylePackageError.append("There was an error loading style package settings. Please, contact administrator");
                }
            });
        }
    },

    stylePackageSelected: function (stylePackage) {
        if (!stylePackage) {
            return;
        }
        const documentLanguage = DocxExportCommon.getElementById("document-language").value;

        DocxExportCommon.setCheckbox("cover-page-checkbox", stylePackage.coverPage);

        DocxExportCommon.setSelector("cover-page-selector", stylePackage.coverPage);
        DocxExportCommon.displayIf("cover-page-selector", stylePackage.coverPage, "inline-block")

        DocxExportCommon.setSelector("css-selector", stylePackage.css);
        DocxExportCommon.setSelector("header-footer-selector", stylePackage.headerFooter);
        DocxExportCommon.setSelector("localization-selector", stylePackage.localization);

        DocxExportCommon.setCheckbox("webhooks-checkbox", !!stylePackage.webhooks);
        DocxExportCommon.setSelector("webhooks-selector", stylePackage.webhooks);
        DocxExportCommon.displayIf("webhooks-selector", !!stylePackage.webhooks, "inline-block")

        DocxExportCommon.setValue("paper-size-selector", stylePackage.paperSize || 'A4');
        DocxExportCommon.setValue("headers-color", stylePackage.headersColor);
        DocxExportCommon.setValue("orientation-selector", stylePackage.orientation || 'PORTRAIT');
        DocxExportCommon.setCheckbox("fit-to-page", stylePackage.fitToPage);
        DocxExportCommon.setCheckbox("enable-comments-rendering", stylePackage.renderComments);
        DocxExportCommon.setCheckbox("watermark", stylePackage.watermark);
        DocxExportCommon.setCheckbox("mark-referenced-workitems", stylePackage.markReferencedWorkitems);
        DocxExportCommon.setCheckbox("cut-empty-chapters", stylePackage.cutEmptyChapters);
        DocxExportCommon.setCheckbox("cut-empty-wi-attributes", stylePackage.cutEmptyWorkitemAttributes);
        DocxExportCommon.setCheckbox("cut-urls", stylePackage.cutLocalURLs);
        DocxExportCommon.setCheckbox("presentational-hints", stylePackage.followHTMLPresentationalHints);

        DocxExportCommon.setCheckbox("custom-list-styles", stylePackage.customNumberedListStyles);
        DocxExportCommon.setValue("numbered-list-styles", stylePackage.customNumberedListStyles || "");
        DocxExportCommon.displayIf("numbered-list-styles", stylePackage.customNumberedListStyles);

        DocxExportCommon.setCheckbox("specific-chapters", stylePackage.specificChapters);
        DocxExportCommon.setValue("chapters", stylePackage.specificChapters || "");
        DocxExportCommon.displayIf("chapters", stylePackage.specificChapters);

        DocxExportCommon.setCheckbox("localization", stylePackage.language);
        DocxExportCommon.setValue("language", (stylePackage.exposeSettings && stylePackage.language && documentLanguage) ? documentLanguage : stylePackage.language);
        DocxExportCommon.displayIf("language", stylePackage.language);

        DocxExportCommon.setCheckbox("selected-roles", stylePackage.linkedWorkitemRoles);
        DocxExportCommon.querySelectorAll(`#roles-selector option`).forEach(roleOption => {
            roleOption.selected = false;
        });
        if (stylePackage.linkedWorkitemRoles) {
            for (const role of stylePackage.linkedWorkitemRoles) {
                DocxExportCommon.querySelectorAll(`#roles-selector option[value='${role}']`).forEach(roleOption => {
                    roleOption.selected = true;
                });
            }
        }
        DocxExportCommon.displayIf("roles-wrapper", stylePackage.linkedWorkitemRoles);

        DocxExportCommon.displayIf("style-package-content", stylePackage.exposeSettings);
    },

    setClass: function (elementId, className) {
        DocxExportCommon.getElementById(elementId).className = className;
    },

    prepareRequest: function (projectId, locationPath, baselineRevision, revision, fileName, targetFormat) {
        let selectedChapters = null;
        if (DocxExportCommon.getElementById("specific-chapters").checked) {
            selectedChapters = this.getSelectedChapters();
            this.setClass("chapters", selectedChapters ? "" : "error");
            if (!selectedChapters) {
                DocxExportCommon.getJQueryElement("#export-error").append("Please, provide comma separated list of integer values in chapters field");
                return undefined;
            }
        }

        let numberedListStyles = null;
        if (DocxExportCommon.getElementById("custom-list-styles").checked) {
            numberedListStyles = DocxExportCommon.getElementById("numbered-list-styles").value;
            const error = this.validateNumberedListStyles(numberedListStyles);
            this.setClass("numbered-list-styles", error ? "error" : "");
            if (error) {
                DocxExportCommon.getJQueryElement("#export-error").append(error);
                return undefined;
            }
        }

        const selectedRoles = [];
        if (DocxExportCommon.getElementById("selected-roles").checked) {
            const selectedOptions = Array.from(DocxExportCommon.getElementById("roles-selector").options).filter(opt => opt.selected);
            selectedRoles.push(...selectedOptions.map(opt => opt.value));
        }

        return this.buildRequestJson(projectId, locationPath, baselineRevision, revision, selectedChapters, numberedListStyles, selectedRoles, fileName, targetFormat);
    },

    buildRequestJson: function (projectId, locationPath, baselineRevision, revision, selectedChapters, numberedListStyles, selectedRoles, fileName, targetFormat) {
        const urlSearchParams = new URL(window.location.href.replace('#', '/')).searchParams;
        return new DocxExportParams.Builder(DocxExportParams.DocumentType.LIVE_DOC)
            .setProjectId(projectId)
            .setLocationPath(locationPath)
            .setBaselineRevision(baselineRevision)
            .setRevision(revision)
            .setCoverPage(DocxExportCommon.getElementById("cover-page-checkbox").checked ? DocxExportCommon.getElementById("cover-page-selector").value : null)
            .setCss(DocxExportCommon.getElementById("css-selector").value)
            .setHeaderFooter(DocxExportCommon.getElementById("header-footer-selector").value)
            .setLocalization(DocxExportCommon.getElementById("localization-selector").value)
            .setWebhooks(DocxExportCommon.getElementById("webhooks-checkbox").checked ? DocxExportCommon.getElementById("webhooks-selector").value : null)
            .setHeadersColor(DocxExportCommon.getElementById("headers-color").value)
            .setPaperSize(DocxExportCommon.getElementById("paper-size-selector").value)
            .setOrientation(DocxExportCommon.getElementById("orientation-selector").value)
            .setTargetFormat(targetFormat)
            .setFitToPage(DocxExportCommon.getElementById('fit-to-page').checked)
            .setEnableCommentsRendering(DocxExportCommon.getElementById('enable-comments-rendering').checked)
            .setWatermark(DocxExportCommon.getElementById("watermark").checked)
            .setMarkReferencedWorkitems(DocxExportCommon.getElementById("mark-referenced-workitems").checked)
            .setCutEmptyChapters(DocxExportCommon.getElementById("cut-empty-chapters").checked)
            .setCutEmptyWIAttributes(DocxExportCommon.getElementById('cut-empty-wi-attributes').checked)
            .setCutLocalUrls(DocxExportCommon.getElementById("cut-urls").checked)
            .setFollowHTMLPresentationalHints(DocxExportCommon.getElementById("presentational-hints").checked)
            .setNumberedListStyles(numberedListStyles)
            .setChapters(selectedChapters)
            .setLanguage(DocxExportCommon.getElementById('localization').checked ? DocxExportCommon.getElementById("language").value : null)
            .setLinkedWorkitemRoles(selectedRoles)
            .setFileName(fileName)
            .setUrlQueryParameters(Object.fromEntries([...urlSearchParams]))
            .build()
            .toJSON();
    },

    getSelectedChapters: function () {
        const chaptersValue = DocxExportCommon.getElementById("chapters").value;
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
    },

    validateNumberedListStyles: function (numberedListStyles) {
        if (!numberedListStyles || numberedListStyles.trim().length === 0) {
            // Stop processing if empty
            return "Please, provide some value";
        } else if (numberedListStyles.match("[^1aAiI]+")) {
            // Stop processing if not valid styles
            return "Please, provide any combination of characters '1aAiI'";

        }
        return undefined;
    },

    loadDocx: function (projectId, locationPath, baselineRevision, revision) {
        //clean previous errors
        DocxExportCommon.getJQueryElement("#export-error").empty();
        DocxExportCommon.getJQueryElement("#export-warning").empty();

        let fileName = DocxExportCommon.getElementById("docx-filename").value;
        if (!fileName) {
            fileName = DocxExportCommon.getElementById("docx-filename").dataset.default;
        }
        if (fileName && !fileName.endsWith(".docx")) {
            fileName += ".docx";
        }

        let request = this.prepareRequest(projectId, locationPath, baselineRevision, revision, fileName, "docx");
        if (request === undefined) {
            return;
        }

        this.actionInProgress(true);

        DocxExportCommon.asyncConvertDoc(request, result => {
            if (result.warning) {
                DocxExportCommon.getJQueryElement("#export-warning").append(result.warning);
            }
            this.actionInProgress(false);

            DocxExportCommon.downloadBlob(result.response, fileName);
        }, errorResponse => {
            this.actionInProgress(false);
            errorResponse.text().then(errorJson => {
                const error = errorJson && JSON.parse(errorJson);
                const errorMessage = error && (error.message ? error.message : error.errorMessage);
                DocxExportCommon.getJQueryElement("#export-error").append("Error occurred during DOCX generation" + (errorMessage ? ":<br>" + errorMessage : ""));
            });
        });
    },
    actionInProgress: function (inProgress) {
        if (inProgress) {
            //disable components
            DocxExportCommon.getJQueryElement(":input").attr("disabled", true);
            //show loading icon
            DocxExportCommon.getJQueryElement("#export-docx-progress").show();
        } else {
            //enable components
            DocxExportCommon.getJQueryElement(":input").attr("disabled", false);
            //hide loading icon
            DocxExportCommon.getJQueryElement("#export-docx-progress").hide();
        }
    },
}
