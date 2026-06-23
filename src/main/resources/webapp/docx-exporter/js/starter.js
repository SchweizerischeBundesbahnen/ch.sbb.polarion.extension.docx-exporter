/*
 * Thin starter — extension-specific config only. The reusable engine (DLE toolbar selectors +
 * self-healing re-injection via MutationObserver) lives in generic:
 * /polarion/docx-exporter/ui/generic/js/dle-toolbar-starter.js
 *
 * The dleEditorHead config is unchanged: it still loads this script and calls
 * DocxExporterStarter.injectToolbar({...}); the bootstrap below pulls the engine and queues
 * the call until it is ready.
 */
(function () {
    const timestampParam = `?timestamp=${Date.now()}`;

    const TOOLBAR_HTML = `
        <table class="dleToolBarTable">
            <tr class="dleToolBarRow">
                <td class="dleToolBarTableCell" title="Export to DOCX">
                    <div class="dleToolBarSingleButton dleToolBarButton"
                     onclick="import('/polarion/docx-exporter/ui/js/modules/ExportPopup.js')
                            .then(module => new module.default())
                            .catch(console.error);">
                        <img class="polarion-MenuButton-Icon" src="/polarion/ria/images/dle/operations/actionMsWordRoundtrip16.svg${timestampParam}" style="margin: 0">
                        <span style="margin: 0 5px 0 10px; font-weight: bold;">Export to DOCX</span>
                    </div>
                </td>
            </tr>
        </table>`;

    const ALTERNATE_TOOLBAR_HTML = `
        <table class="dleToolBarTable">
            <tr class="dleToolBarRow">
                <td ><div class="gwt-Label polarion-dle-toolbar-Padding"></div></td>
                <td><img src="/polarion/ria/images/toolbar_splitter_gray.gif${timestampParam}" class="gwt-Image polarion-dle-ToolbarPanel-separator"></td>
                <td ><div class="gwt-Label polarion-dle-toolbar-Padding"></div></td>
                <td class="dleToolBarTableCell" title="Export to DOCX">
                    <div class="dleToolBarSingleButton dleToolBarButton"
                    onclick="import('/polarion/docx-exporter/ui/js/modules/ExportPopup.js')
                            .then(module => new module.default())
                            .catch(console.error);">
                        <img class="polarion-MenuButton-Icon" src="/polarion/ria/images/dle/operations/actionMsWordRoundtrip16.svg${timestampParam}" style="margin: 0">
                    </div>
                </td>
            </tr>
        </table>`;

    let starter = null, myOrder;
    const pending = [];
    window.DocxExporterStarter = {
        injectToolbar: function (params) {
            if (myOrder === undefined) {
                // Capture config-execution order (this stub runs synchronously in dleEditorHead
                // order) so multiple toolbar buttons keep a stable left-to-right order on re-render.
                const seq = top.__genericDleToolbarSeq || (top.__genericDleToolbarSeq = { n: 0 });
                myOrder = seq.n++;
            }
            starter ? starter.injectToolbar(params) : pending.push(params);
        }
    };

    const engine = document.createElement('script');
    engine.src = `/polarion/docx-exporter/ui/generic/js/dle-toolbar-starter.js${timestampParam}`;
    engine.onload = function () {
        const generic = window.GenericDleToolbarStarter;
        if (!generic) {
            console.error("docx-exporter: GenericDleToolbarStarter is not available after the engine loaded — toolbar injection skipped.");
            pending.length = 0;
            return;
        }
        generic.injectStyles("docx-exporter-styles", `/polarion/docx-exporter/css/docx-exporter.css${timestampParam}`);
        generic.injectStyles("docx-micromodal-styles", `/polarion/docx-exporter/ui/generic/css/micromodal.css${timestampParam}`);
        generic.injectStyles("generic-checkbox-styles", `/polarion/docx-exporter/ui/generic/css/checkboxes.css${timestampParam}`);
        generic.injectScript("docx-micromodal-script", `/polarion/docx-exporter/js/micromodal.min.js${timestampParam}`);
        starter = generic.create({
            markerId: 'docx-exporter-toolbar-injected',
            alternateHtml: ALTERNATE_TOOLBAR_HTML,
            defaultHtml: TOOLBAR_HTML,
            order: myOrder
        });
        pending.forEach(params => starter.injectToolbar(params));
        pending.length = 0;
    };
    engine.onerror = function () {
        console.error("docx-exporter: failed to load the DLE toolbar engine — toolbar injection skipped.");
        pending.length = 0;
    };
    document.head.appendChild(engine);
})();
