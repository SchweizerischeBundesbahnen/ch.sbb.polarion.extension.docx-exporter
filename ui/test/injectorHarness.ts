// Shared setup for the *.node.test.ts suites that cover the product injector scripts in
// src/main/resources/webapp/docx-exporter/js/ (dle-toolbar.js, starter.js).
//
// Those are plain IIFEs, not modules: they read `document.currentScript` and `top` at load time and
// leave their state on `top`. So every test needs the globals in place BEFORE the import, a fresh
// evaluation (vi.resetModules() plus a dynamic import), and a clean `top` afterwards - jsdom does not
// recreate the window between test files in the same project.
//
// In jsdom top === window, which is what production looks like: these scripts are loaded through
// Polarion's scriptInjection into the main page, not into an editor iframe. See vitest.config.ts for
// why they are not tested in browser mode.

export type Globals = Record<string, unknown>;

/** `top` as a writable bag, which is how the injectors treat it. */
export const globals = (): Globals => window as unknown as Globals;

/** Lets the microtask queue settle, so a dynamic import and any load handler have finished. */
export const flushPromises = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 0));

/**
 * The <script> tag the Polarion scriptInjection config would produce. Not appended: the injectors read
 * `document.currentScript` to derive their extension base URL, and appending it would also show up in
 * the assertions that count what a script put on the page.
 */
export const setCurrentScript = (src: string): HTMLScriptElement => {
  const tag = document.createElement('script');
  tag.src = src;
  Object.defineProperty(document, 'currentScript', { value: tag, configurable: true });
  return tag;
};

/** Everything an injector leaves behind, plus the location hash they read the project id from. */
export const resetInjectorGlobals = (): void => {
  document.head.innerHTML = '';
  document.body.innerHTML = '';
  delete globals().DocxExporterStarter;
  delete globals().CommonDleToolbarStarter;
  window.location.hash = '';
};
