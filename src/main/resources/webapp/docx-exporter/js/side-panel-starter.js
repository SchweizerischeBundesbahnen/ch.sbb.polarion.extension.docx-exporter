const DocxExporterSidePanelStarter = {
    timestampParam: `?timestamp=${Date.now()}`,

    injectAll: function () {
        this.injectScript("docx-common-script", `/polarion/docx-exporter/ui/generic/js/common.js${this.timestampParam}`);
        this.injectScript("docx-ExportParams-script", `/polarion/docx-exporter/ui/js/modules/ExportParams.js${this.timestampParam}`, 'module');
        this.injectScript("docx-export-common-script", `/polarion/docx-exporter/ui/js/export-common.js${this.timestampParam}`);
        this.injectScript("docx-export-script", `/polarion/docx-exporter/ui/js/export-docx.js${this.timestampParam}`);
    },


    injectScript: function (id, componentScriptPath, type = "text/javascript") {
        if (top.document.body && !top.document.getElementById(id)) {
            try {
                const scriptElement = document.createElement("script");
                scriptElement.id = id;
                scriptElement.setAttribute("src", componentScriptPath);
                scriptElement.setAttribute("type", type);
                top.document.body.appendChild(scriptElement);
            } catch (err) {
                console.log(`Couldn't inject script: ${componentScriptPath}: ${err}`);
            }
        }
    },
}

DocxExporterSidePanelStarter.injectAll();
