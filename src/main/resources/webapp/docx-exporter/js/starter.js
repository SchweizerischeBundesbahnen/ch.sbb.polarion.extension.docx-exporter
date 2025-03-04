(function () {
    const timestampParam = `?timestamp=${Date.now()}`;

    function injectStyles(id, stylesPath) {
        if (!top.document.getElementById(id)) {
            const styleElement = document.createElement("link");
            styleElement.id = id;
            styleElement.rel = "stylesheet";
            styleElement.type = "text/css";
            styleElement.href = stylesPath;
            top.document.head.appendChild(styleElement);
        }
    }

    function injectScript(id, componentScriptPath) {
        if (!top.document.getElementById(id)) {
            const scriptElement = document.createElement("script");
            scriptElement.id = id;
            scriptElement.setAttribute("src", componentScriptPath);
            scriptElement.setAttribute("type", "text/javascript");
            top.document.head.appendChild(scriptElement);
        }
    }

    injectStyles("docx-exporter-styles", `/polarion/docx-exporter/css/docx-exporter.css${timestampParam}`);
    injectStyles("docx-micromodal-styles", `/polarion/docx-exporter/css/micromodal.css${timestampParam}`);
    injectScript("docx-micromodal-script", `/polarion/docx-exporter/js/micromodal.min.js${timestampParam}`);

    const TOOLBAR_HTML = `
        <table class="dleToolBarTable">
            <tr class="dleToolBarRow">
                <td class="dleToolBarTableCell" title="Export to DOCX">
                    <div class="dleToolBarSingleButton dleToolBarButton"
                     onclick="import('/polarion/docx-exporter/ui/js/modules/ExportPopup.js')
                            .then(module => new module.default())
                            .catch(console.error);">
                        <img class="polarion-MenuButton-Icon" src="/polarion/ria/images/dle/operations/actionMsWordRoundtrip16.svg{TIMESTAMP_PARAM}" style="margin: 0">
                        <span style="margin: 0 5px 0 10px; font-weight: bold;">Export to DOCX</span>
                    </div>
                </td>
            </tr>
        </table>`;

    const ALTERNATE_TOOLBAR_HTML = `
        <table class="dleToolBarTable">
            <tr class="dleToolBarRow">
                <td ><div class="gwt-Label polarion-dle-toolbar-Padding"></div></td>
                <td><img src="/polarion/ria/images/toolbar_splitter_gray.gif{TIMESTAMP_PARAM}" class="gwt-Image polarion-dle-ToolbarPanel-separator"></td>
                <td ><div class="gwt-Label polarion-dle-toolbar-Padding"></div></td>
                <td class="dleToolBarTableCell" title="Export to DOCX">
                    <div class="dleToolBarSingleButton dleToolBarButton"
                    onclick="import('/polarion/docx-exporter/ui/js/modules/ExportPopup.js')
                            .then(module => new module.default())
                            .catch(console.error);">
                        <img class="polarion-MenuButton-Icon" src="/polarion/ria/images/dle/operations/actionMsWordRoundtrip16.svg{TIMESTAMP_PARAM}" style="margin: 0">
                    </div>
                </td>
            </tr>
        </table>`;

    window.DocxExporterStarter = {
        injectToolbar: function (params) {
            if (params?.alternate) {
                const toolbarParent = top.document.querySelector('div.polarion-content-container div.polarion-Container div.polarion-dle-Container > div.polarion-dle-Wrapper > div.polarion-dle-RpcPanel > div.polarion-dle-MainDockPanel div.polarion-rte-ToolbarPanelWrapper table.polarion-dle-ToolbarPanel tr');
                const toolbarContainer = document.createElement('td');
                toolbarContainer.innerHTML = ALTERNATE_TOOLBAR_HTML.replaceAll("{TIMESTAMP_PARAM}", timestampParam);
                toolbarParent.insertBefore(toolbarContainer, toolbarParent.querySelector('td[width="100%"]'));
            } else {
                const documentFrame = top.document.querySelector('div.polarion-content-container div.polarion-Container div.polarion-dle-Container>div.polarion-dle-Wrapper>div.polarion-dle-RpcPanel>div.polarion-dle-MainDockPanel div.polarion-dle-SplitPanel:last-child .polarion-dle-RichTextArea');
                const toolbarContainer = document.createElement('div');
                toolbarContainer.classList.add("dleToolBarContainer");
                toolbarContainer.style.marginRight = "14px";
                toolbarContainer.innerHTML = TOOLBAR_HTML.replaceAll("{TIMESTAMP_PARAM}", timestampParam);
                documentFrame.parentNode.parentNode.prepend(toolbarContainer);
            }
        }
    }

})();
