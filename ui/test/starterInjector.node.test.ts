import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { engineRecorder, flushPromises, globals, resetInjectorGlobals, setCurrentScript } from './injectorHarness';

// starter.js is the deprecated-but-supported document-editor entry point: dleEditorHead loads it and
// calls DocxExporterStarter.injectToolbar({...}). It exposes that global synchronously, pulls generic's
// shared toolbar engine, and replays whatever was queued in between.
//
// The import specifier must be written out in full at every call site - see the note in
// injectorHarness.ts. The file lives outside the Vite root, and a specifier held in a variable is
// resolved against the root instead of against this file.

const SELF_URL = 'http://localhost/polarion/docx-exporter/js/starter.js';

describe('starter.js injector', () => {
  let consoleError: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.resetModules();
    resetInjectorGlobals();
    consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    consoleError.mockRestore();
  });

  /** The engine <script> starter.js appends to the page head. */
  const engineScript = (): HTMLScriptElement => {
    const script = document.head.querySelector<HTMLScriptElement>('script[src*="dle-toolbar-starter.js"]');
    if (!script) {
      throw new Error('starter.js did not append the engine script');
    }
    return script;
  };

  const load = async (selfUrl = SELF_URL): Promise<void> => {
    setCurrentScript(selfUrl);
    await import('../../src/main/resources/webapp/docx-exporter/js/starter.js');
    await flushPromises();
  };

  const starter = (): { injectToolbar: (params?: unknown) => void } =>
    globals().DocxExporterStarter as { injectToolbar: (params?: unknown) => void };

  /** Load the script and hand it a recording engine, which is the state most assertions need. */
  const loadWithEngine = async (selfUrl = SELF_URL): Promise<ReturnType<typeof engineRecorder>> => {
    const engine = engineRecorder();
    await load(selfUrl);
    globals().GenericDleToolbarStarter = engine.stub;
    engineScript().dispatchEvent(new window.Event('load'));
    await flushPromises();
    return engine;
  };

  it('exposes DocxExporterStarter before the engine has loaded', async () => {
    await load();

    // The dleEditorHead config calls injectToolbar in the same synchronous pass that loads this script,
    // so the global cannot wait for the engine.
    expect(starter()).toBeDefined();
    expect(typeof starter().injectToolbar).toBe('function');
  });

  it('appends the shared engine script, its URL derived from its own', async () => {
    // A non-default web context proves the base is read from document.currentScript rather than
    // hardcoded to /polarion/docx-exporter/.
    await load('http://localhost/polarion/my-ctx/js/starter.js');

    expect(engineScript().src).toContain('http://localhost/polarion/my-ctx/ui/generic/js/dle-toolbar-starter.js');
  });

  it('puts nothing on the page but the engine script', async () => {
    await loadWithEngine();

    // Regression guard for the block that used to inject micromodal plus six generic control
    // stylesheets: the export dialog is a React module that styles itself inside its own shadow root,
    // and the button's .dleToolBar* rules come from generic's css/dle-toolbar.css, which the toolbar
    // engine injects. Nothing else may reappear here.
    expect(document.querySelectorAll('link')).toHaveLength(0);
    expect(document.querySelectorAll('style')).toHaveLength(0);
    expect([...document.querySelectorAll('script')]).toEqual([engineScript()]);
  });

  it('queues injectToolbar calls made before the engine loads, then replays them in order', async () => {
    const engine = engineRecorder();
    await load();

    starter().injectToolbar({ alternate: true });
    starter().injectToolbar({ alternate: false });
    expect(engine.injectToolbarCalls).toHaveLength(0); // nothing to inject into yet

    globals().GenericDleToolbarStarter = engine.stub;
    engineScript().dispatchEvent(new window.Event('load'));
    await flushPromises();

    expect(engine.createdConfigs).toHaveLength(1);
    expect(engine.injectToolbarCalls).toEqual([{ alternate: true }, { alternate: false }]);
  });

  it('passes injectToolbar straight through once the engine is loaded', async () => {
    const engine = await loadWithEngine();

    starter().injectToolbar({ alternate: true });

    expect(engine.injectToolbarCalls).toEqual([{ alternate: true }]);
  });

  it('hands both toolbar layouts and the popup call to the engine', async () => {
    const engine = await loadWithEngine();

    const config = engine.createdConfigs[0];
    expect(config.markerId).toBe('docx-exporter-toolbar-injected');
    for (const html of [config.defaultHtml, config.alternateHtml]) {
      // A stringly-typed contract with the React module: no compile-time link ties these together.
      expect(html).toContain('/polarion/docx-exporter-app/ui/app/assets/export-popup.js');
      // No argument: this extension exports Live Documents only, so the module needs no document type.
      expect(html).toContain('module.openExportPopup()');
      expect(html).not.toContain('documentType');
    }
    // Only the alternate layout sits inside the toolbar row, between two native separators.
    expect(config.alternateHtml).toContain('toolbar_splitter_gray.gif');
    expect(config.defaultHtml).not.toContain('toolbar_splitter_gray.gif');
  });

  it('builds the permission URL with the project id read from the location hash', async () => {
    window.location.hash = '#/project/elibrary/wiki/Documents';
    const engine = await loadWithEngine();

    expect(engine.createdConfigs[0].permissionCheckUrl).toBe(
      'http://localhost/polarion/docx-exporter/rest/internal/permissions/export?projectId=elibrary',
    );
  });

  it('URL-encodes a project id that needs it', async () => {
    // The hash is decoded before the id is matched, so an id with a space arrives as one token and has
    // to be re-encoded for the query string.
    window.location.hash = '#/project/my%20project/wiki';
    const engine = await loadWithEngine();

    expect(engine.createdConfigs[0].permissionCheckUrl).toContain('?projectId=my%20project');
  });

  it('omits the project id when the hash carries no project scope', async () => {
    window.location.hash = '#/dashboard';
    const engine = await loadWithEngine();

    // Only the global roles apply then; the endpoint takes no projectId.
    expect(engine.createdConfigs[0].permissionCheckUrl).toBe(
      'http://localhost/polarion/docx-exporter/rest/internal/permissions/export',
    );
  });

  it('takes its order from the shared cross-extension registry', async () => {
    const engine = engineRecorder();
    // Two other extensions registered first, so this button keeps its place on a re-render.
    globals().__genericDleToolbarSeq = { n: 2 };
    await load();

    starter().injectToolbar({ alternate: true });
    globals().GenericDleToolbarStarter = engine.stub;
    engineScript().dispatchEvent(new window.Event('load'));
    await flushPromises();

    expect(engine.createdConfigs[0].order).toBe(2);
    expect(globals().__genericDleToolbarSeq).toEqual({ n: 3 });
  });

  it('logs and drops the queue when the engine loads without defining its global', async () => {
    const engine = engineRecorder();
    await load();
    starter().injectToolbar({ alternate: true });

    engineScript().dispatchEvent(new window.Event('load'));
    await flushPromises();

    expect(consoleError).toHaveBeenCalledWith(expect.stringContaining('GenericDleToolbarStarter is not available'));

    // The queue is dropped, not held: a later engine arrival must not inject a stale button.
    globals().GenericDleToolbarStarter = engine.stub;
    engineScript().dispatchEvent(new window.Event('load'));
    await flushPromises();
    expect(engine.injectToolbarCalls).toHaveLength(0);
  });

  it('logs and drops the queue when the engine script fails to load', async () => {
    const engine = engineRecorder();
    await load();
    starter().injectToolbar({ alternate: true });

    engineScript().dispatchEvent(new window.Event('error'));
    await flushPromises();

    expect(consoleError).toHaveBeenCalledWith(expect.stringContaining('failed to load the DLE toolbar engine'));

    globals().GenericDleToolbarStarter = engine.stub;
    engineScript().dispatchEvent(new window.Event('load'));
    await flushPromises();
    expect(engine.injectToolbarCalls).toHaveLength(0);
  });
});
