/*
 * The extension's toolbar button, injected into every Polarion page without any administrator
 * configuration. external-js/docx-exporter-ui-loader.js loads this script into the main portal page;
 * see plugin.xml for how Polarion gets it there.
 *
 * Whether the button is drawn is read per project from the `appearance` setting (the Appearance
 * administration page), which a project inherits from the Default Repository unless it saves its own.
 * Polarion is a single-page application: the user moves to another project without reloading the page,
 * and this script runs once. So the configuration is re-read on every project change and the button is
 * rebuilt for the project the user is now in.
 *
 * The button goes into the document editor toolbar through the shared self-healing engine of
 * react-sbb-polarion (CommonDleToolbarStarter), which re-injects it after a GWT re-render. This
 * extension exports Live Documents only, so it has no Live Report or Classic Wiki button.
 *
 * Nothing else is put on the page: the button uses Polarion's own toolbar classes, and the export
 * dialog is a React module which mounts into a shadow root of its own, styles included.
 */
(function () {
    'use strict';

    // One timestamp per page load: a click reuses what the previous click loaded, while an updated
    // extension is still picked up on the next page open.
    const timestampParam = `?timestamp=${Date.now()}`;
    const EXT_BASE = '/polarion/docx-exporter/';
    const APP_BASE = '/polarion/docx-exporter-app/ui/app/';

    // The export dialog is a React module of the docx-exporter-app webapp, imported on click. It mounts
    // into a shadow root of its own, so nothing has to be injected into the page for it.
    const POPUP_MODULE = `${APP_BASE}assets/export-popup.js${timestampParam}`;
    // The toolbar engine is a react-sbb-polarion artifact, copied next to this extension's built app.
    const ENGINE_SRC = `${APP_BASE}assets/dle-toolbar-starter.js${timestampParam}`;
    const APPEARANCE_URL = `${EXT_BASE}rest/internal/appearance`;

    const ICON_URL = '/polarion/ria/images/dle/operations/actionMsWordRoundtrip16.svg';
    const TITLE = 'Export to DOCX';

    // The id the scriptInjection injector of earlier versions used, kept so that a button it left on a
    // page is recognized rather than drawn a second time next to it.
    const LIVE_DOC_MARKER = 'docx-exporter-toolbar-injected';

    // The position of this button among the buttons other extensions put into the same toolbar row.
    // The engine sorts by this number, and on equal numbers by whichever button injects first, which
    // changes between page loads. So each extension on the engine keeps a number of its own:
    // pdf-exporter 10, docx-exporter 20, the next one 30.
    const TOOLBAR_ORDER = 20;

    // Polarion's own project-id charset. Anything else in the hash segment is not a project id, so it
    // is not passed on to the server.
    const PROJECT_ID_PATTERN = /^[\w.-]+$/;

    /* ---------------------------------------------------------------------------------------- */
    /* The engine                                                                                  */
    /* ---------------------------------------------------------------------------------------- */

    // Deliberately NOT generic's engine id or promise key. The two engines export different globals
    // (GenericDleToolbarStarter vs CommonDleToolbarStarter), so sharing the loader bookkeeping would
    // hand this file an engine it cannot use - or, when the other extension's tag is already in the
    // document, leave the promise waiting for a load event which has already fired. What the two DO
    // share, and must keep sharing, are the registries on `top`: button order and the injected
    // stylesheet are coordinated through those, not through how the script got loaded.
    const ENGINE_ID = 'common-dle-toolbar-engine';

    function loadEngine() {
        if (!window.__commonDleToolbarEnginePromise) {
            window.__commonDleToolbarEnginePromise = new Promise((resolve, reject) => {
                if (window.CommonDleToolbarStarter) {
                    resolve();
                    return;
                }
                const existing = document.getElementById(ENGINE_ID);
                if (existing) {
                    existing.addEventListener('load', resolve);
                    existing.addEventListener('error', reject);
                    return;
                }
                const script = document.createElement('script');
                script.id = ENGINE_ID;
                script.setAttribute('src', ENGINE_SRC);
                script.setAttribute('type', 'text/javascript');
                script.onload = resolve;
                script.onerror = reject;
                document.head.appendChild(script);
            });
        }
        return window.__commonDleToolbarEnginePromise;
    }

    /* ---------------------------------------------------------------------------------------- */
    /* State                                                                                       */
    /* ---------------------------------------------------------------------------------------- */

    // Only the newest load of this script drives the page. The loader appends it once, but a user who
    // re-runs it would otherwise end up with two copies racing to build and tear down the same button.
    const installToken = {};
    window.__docxExporterToolbarInstall = installToken;
    const installed = () => window.__docxExporterToolbarInstall === installToken;

    /** Appearance per project id, so moving back and forth does not ask the server again. */
    const appearanceCache = new Map();
    /**
     * The project the newest rebuild was started for; undefined before the first one, and again when
     * that rebuild failed, so a later visit to the same project tries once more. Set synchronously,
     * because Polarion's own hash change arrives next to the one this script already acted on and both
     * would otherwise queue a rebuild of their own.
     */
    let requestedFor;
    /** Serializes the rebuilds, so a burst of hash changes cannot interleave two of them. */
    let pending = Promise.resolve();

    let liveDocStarter = null;

    /** The current project id, parsed from Polarion's location hash, or '' outside a project. */
    function currentProjectId() {
        try {
            const match = /project\/([^/]+)\//.exec(decodeURI(window.location.hash || ''));
            const projectId = match ? match[1] : '';
            return PROJECT_ID_PATTERN.test(projectId) ? projectId : '';
        } catch {
            return '';
        }
    }

    function readAppearance(projectId) {
        if (appearanceCache.has(projectId)) {
            return Promise.resolve(appearanceCache.get(projectId));
        }
        const url = projectId ? `${APPEARANCE_URL}?projectId=${encodeURIComponent(projectId)}` : APPEARANCE_URL;
        return fetch(url, {credentials: 'same-origin'})
            .then(response => {
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }
                return response.json();
            })
            .then(appearance => {
                appearanceCache.set(projectId, appearance);
                return appearance;
            });
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Building and tearing down the button                                                        */
    /* ---------------------------------------------------------------------------------------- */

    function teardown(starter, markerId) {
        starter?.destroy();
        document.getElementById(markerId)?.remove();
        return null;
    }

    function build(engine, appearance) {
        if (!appearance.liveDocButton) {
            return;
        }
        liveDocStarter = engine.create({
            markerId: LIVE_DOC_MARKER,
            html: engine.buildButtonHtml({
                title: TITLE,
                iconUrl: `${ICON_URL}${timestampParam}`,
                onClick: `import('${POPUP_MODULE}')
                    .then(module => module.openExportPopup())
                    .catch(console.error);`
            }),
            target: 'dleEditor',
            order: TOOLBAR_ORDER,
            // Already answered by the same request the appearance came from, so the engine gets a
            // function rather than a URL it would call a second time. Fail-closed is kept: a request
            // which did not answer never reaches this point, and the button is not built.
            permissionCheck: () => appearance.permitted
        });
        liveDocStarter.injectToolbar();
    }

    /**
     * Rebuild the button for the project the user is now in. A project whose configuration cannot be
     * read keeps the button of the project before it rather than losing it to a network blip: the
     * server decides what may be exported, this only decides what is drawn.
     */
    function applyCurrentProject() {
        const projectId = currentProjectId();
        if (!installed() || projectId === requestedFor) {
            return;
        }
        requestedFor = projectId;
        pending = pending
            .then(() => Promise.all([loadEngine(), readAppearance(projectId)]))
            .then(([, appearance]) => {
                // Another hash change, or a newer load of this script, may have overtaken this one
                // while it waited.
                if (!installed() || currentProjectId() !== projectId) {
                    return;
                }
                liveDocStarter = teardown(liveDocStarter, LIVE_DOC_MARKER);
                build(window.CommonDleToolbarStarter, appearance);
            })
            .catch(error => {
                if (requestedFor === projectId) {
                    requestedFor = undefined;
                }
                console.error('docx-exporter: could not configure the toolbar button.', error);
            });
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Start                                                                                       */
    /* ---------------------------------------------------------------------------------------- */

    let scheduled = false;
    // Coalesce a burst of GWT mutations into a single pass. The observer notices that the user is now
    // in another project, which `hashchange` alone cannot be trusted with: Polarion's own navigation
    // sets the hash in ways which do not always raise that event, and a project change which went
    // unnoticed would leave the page with the button of the project before it for the rest of the
    // session. The check is a string comparison when nothing changed, so running it on every render
    // costs nothing.
    new MutationObserver(() => {
        if (!scheduled) {
            scheduled = true;
            requestAnimationFrame(() => {
                scheduled = false;
                if (installed()) {
                    applyCurrentProject();
                }
            });
        }
    }).observe(document.documentElement, {childList: true, subtree: true});

    window.addEventListener('hashchange', applyCurrentProject);
    applyCurrentProject();
})();
