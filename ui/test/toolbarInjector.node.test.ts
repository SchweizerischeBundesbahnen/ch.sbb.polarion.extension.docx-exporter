import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { engineRecorder, flushPromises, globals, resetInjectorGlobals } from './injectorHarness';

// toolbar-injector.js is what external-js/docx-exporter-ui-loader.js loads into the main Polarion page.
// It replaces the scriptInjection injector and draws the button only when the `appearance` setting of
// the current project asks for it, re-reading that setting whenever the user moves to another project.
//
// The import specifier must be written out in full at every call site - see injectorHarness.ts.

const LIVE_DOC_MARKER = 'docx-exporter-toolbar-injected';

/** The button on, which is what an installation that never opened the Appearance page gets. */
const ALL_ON = {
  liveDocButton: true,
  permitted: true,
};

describe('toolbar-injector.js', () => {
  let engine: ReturnType<typeof engineRecorder>;
  let fetchMock: ReturnType<typeof vi.fn>;
  /** The appearance answered per project id; '' is the global scope. */
  let appearances: Record<string, Record<string, boolean>>;
  const observers: MutationObserver[] = [];

  beforeEach(() => {
    vi.resetModules();
    resetInjectorGlobals();
    engine = engineRecorder();
    // The engine is already "loaded": the element id is on the page and the global is defined, so the
    // injector's loadEngine resolves against the stub instead of fetching the real one.
    const engineTag = document.createElement('script');
    engineTag.id = 'common-dle-toolbar-engine';
    document.head.appendChild(engineTag);
    globals().CommonDleToolbarStarter = engine.stub;

    appearances = { '': ALL_ON, elibrary: ALL_ON };
    fetchMock = vi.fn((url: string) => {
      const projectId = new URL(url, 'http://localhost').searchParams.get('projectId') ?? '';
      return Promise.resolve({ ok: true, json: () => Promise.resolve(appearances[projectId]) });
    });
    vi.stubGlobal('fetch', fetchMock);
    // jsdom without pretendToBeVisual has no requestAnimationFrame
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => setTimeout(() => callback(0), 0));
    const Native = window.MutationObserver;
    vi.stubGlobal(
      'MutationObserver',
      class extends Native {
        constructor(callback: MutationCallback) {
          super(callback);
          observers.push(this);
        }
      },
    );
  });

  afterEach(() => {
    observers.splice(0).forEach((observer) => observer.disconnect());
    vi.unstubAllGlobals();
  });

  const load = async (hash = '#/project/elibrary/wiki/Space/Document'): Promise<void> => {
    window.location.hash = hash;
    await import('../../src/main/resources/webapp/docx-exporter/js/toolbar-injector.js');
    await flushPromises();
  };

  const configFor = (markerId: string) => engine.createdConfigs.find((config) => config.markerId === markerId);

  it('reads the appearance of the project in the hash', async () => {
    await load();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe('/polarion/docx-exporter/rest/internal/appearance?projectId=elibrary');
    expect(fetchMock.mock.calls[0][1]).toEqual({ credentials: 'same-origin' });
  });

  it('asks for the global scope outside a project', async () => {
    await load('#/wiki/Home');

    expect(fetchMock.mock.calls[0][0]).toBe('/polarion/docx-exporter/rest/internal/appearance');
  });

  it('creates the document editor button through the shared engine', async () => {
    await load();

    const config = configFor(LIVE_DOC_MARKER);
    expect(config?.target).toBe('dleEditor');
    expect(engine.injectToolbarCalls).toHaveLength(1);
    // The dialog is imported on click from the app's assets.
    expect(config?.html).toContain('/polarion/docx-exporter-app/ui/app/assets/export-popup.js');
    expect(config?.html).toContain('openExportPopup()');
    expect(config?.html).toContain('title="Export to DOCX"');
  });

  it('takes a toolbar position of its own, so the buttons of other extensions do not swap places with it', async () => {
    await load();

    // On equal numbers the engine orders by whichever button injects first, which differs per page load.
    // pdf-exporter takes 10; this extension 20.
    expect(configFor(LIVE_DOC_MARKER)?.order).toBe(20);
  });

  it('answers the permission check from the same response the appearance came from', async () => {
    appearances.elibrary = { ...ALL_ON, permitted: false };
    await load();

    // One request, not a second one to the permissions endpoint.
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(configFor(LIVE_DOC_MARKER)?.permissionCheck?.()).toBe(false);
  });

  it('draws no button when the project switched it off', async () => {
    appearances.elibrary = { ...ALL_ON, liveDocButton: false };
    await load();

    expect(engine.createdConfigs).toHaveLength(0);
  });

  it('rebuilds the button for the project the user moves to', async () => {
    appearances.other = { ...ALL_ON, liveDocButton: false };
    await load();
    expect(configFor(LIVE_DOC_MARKER)).toBeDefined();

    window.location.hash = '#/project/other/wiki/Space/Document';
    window.dispatchEvent(new window.Event('hashchange'));
    await vi.waitFor(() => expect(engine.destroyed).toContain(LIVE_DOC_MARKER));

    // Torn down for the project the user left, and not drawn again for one which switched it off.
    expect(engine.createdConfigs.filter((config) => config.markerId === LIVE_DOC_MARKER)).toHaveLength(1);
  });

  it('notices a project change which raised no hashchange event', async () => {
    appearances.other = { ...ALL_ON, liveDocButton: false };
    await load();
    expect(configFor(LIVE_DOC_MARKER)).toBeDefined();

    // Polarion's own navigation does not always raise hashchange, so the DOM observer has to notice.
    window.location.hash = '#/project/other/wiki/Space/Document';
    document.body.appendChild(document.createElement('div'));
    await vi.waitFor(() => expect(engine.destroyed).toContain(LIVE_DOC_MARKER));

    expect(engine.createdConfigs.filter((config) => config.markerId === LIVE_DOC_MARKER)).toHaveLength(1);
  });

  it('asks the server once per project', async () => {
    await load();
    window.location.hash = '#/project/elibrary/wiki/Space/Other';
    window.dispatchEvent(new window.Event('hashchange'));
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('keeps the current button when the configuration cannot be read', async () => {
    const errors = vi.spyOn(console, 'error').mockImplementation(() => {});
    await load();
    const created = engine.createdConfigs.length;

    fetchMock.mockImplementation(() => Promise.resolve({ ok: false, status: 500 }));
    window.location.hash = '#/project/other/wiki/Space/Document';
    window.dispatchEvent(new window.Event('hashchange'));
    await vi.waitFor(() => expect(errors).toHaveBeenCalled());

    expect(engine.createdConfigs).toHaveLength(created);
    expect(engine.destroyed).toHaveLength(0);
    errors.mockRestore();
  });
});
