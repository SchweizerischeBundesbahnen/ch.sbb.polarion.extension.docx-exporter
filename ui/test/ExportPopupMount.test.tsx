import type { Root } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { page, userEvent } from 'vitest/browser';
import { openExportPopup } from '../src/popup/mount';
import { popupRoutes } from './exportPopupSamples';
import { installFetchMock } from './mockFetch';
import { SAMPLE_STYLE_PACKAGE_FULL } from './sidePanelSamples';

// How the export dialog gets onto the document editor page: starter.js imports assets/export-popup.js and
// calls openExportPopup. It appends a host of its own to the body and mounts into a shadow root of it, so
// nothing may leak either way - the page it opens on is Polarion's, shared with every other extension.
//
// Unlike the other popup suite this one lets the dialog read its real endpoints (behind a fetch mock), since
// what is under test is the mounting: the styles that reach the root, and the location read off the page URL.

const roots: Root[] = [];
const origHash = window.location.hash;

/** The shadow root of the host openExportPopup appended, which is the last child of the body. */
const shadow = () => (document.body.lastElementChild as HTMLElement | null)?.shadowRoot ?? null;

const loaded = () => vi.waitFor(() => expect(shadow()?.querySelector('#popup-style-package-select')).not.toBeNull());

const open = (options: Parameters<typeof openExportPopup>[0] = {}) => {
  installFetchMock(popupRoutes(SAMPLE_STYLE_PACKAGE_FULL));
  const root = openExportPopup(options);
  roots.push(root);
  return root;
};

/** The viewport the rest of the suite runs at, restored after the tests that change it. */
const DEFAULT_VIEWPORT = { width: 1280, height: 720 } as const;

/** Everything inside the dialog that has more to show than it shows, and offers a scrollbar for it. */
const scrollers = (root: ShadowRoot): string[] =>
  [...new Set(root.querySelectorAll('*'))]
    .filter(
      (element) =>
        element.scrollHeight > element.clientHeight + 1 &&
        ['auto', 'scroll'].includes(getComputedStyle(element).overflowY),
    )
    .map((element) => element.className || element.tagName);

/** Where the sample document is, spelled out as the endpoints want it. */
const location = () => ({
  scope: 'project/elibrary/',
  projectId: 'elibrary',
  locationPath: 'Default Space/Cross Link Issue',
  spaceId: 'Default Space',
  documentName: 'Cross Link Issue',
  urlQueryParameters: {},
});

afterEach(async () => {
  await page.viewport(DEFAULT_VIEWPORT.width, DEFAULT_VIEWPORT.height);
  // Unmount before removing the host, so the dialog's own effects run their cleanup
  roots.splice(0).forEach((root) => root.unmount());
  document.querySelectorAll('body > div').forEach((element) => {
    if (element.shadowRoot) element.remove();
  });
  window.history.replaceState({}, '', `${window.location.pathname}${window.location.search}${origHash}`);
  vi.unstubAllGlobals();
  document.cookie = 'selected-style-package=; path=/; max-age=0';
});

describe('mounting the export dialog', () => {
  it('renders the dialog into a shadow root of a host of its own', async () => {
    open({ location: location() });
    await loaded();

    // The page around it sees none of it: the dialog's markup is behind the shadow boundary
    expect(document.querySelector('#popup-style-package-select')).toBeNull();
    expect(shadow()!.querySelector('.rsp-modal')).not.toBeNull();
  });

  it('gives the shadow root the styles the dialog needs', async () => {
    open({ location: location() });
    await loaded();

    const styles = Array.from(shadow()!.querySelectorAll('style')).map((style) => style.textContent ?? '');
    // The design tokens and controls, which react-sbb-polarion's stylesheet brings ...
    expect(styles.some((css) => css.includes('--sbb-checkbox-checked'))).toBe(true);
    // ... the base font, which nothing inside a shadow root inherits from the page ...
    expect(styles.some((css) => css.includes('--sbb-control-font-family'))).toBe(true);
    // ... and the dialog's own two-column layout, which used to be a page stylesheet
    expect(styles.some((css) => css.includes('.flex-column'))).toBe(true);
  });

  it('carries the classes the dialog CSS and the tokens are scoped to', async () => {
    open({ location: location() });
    await loaded();

    const container = shadow()!.querySelector('div')!;
    expect(container.className).toBe('docx-exporter form-wrapper sbb-ui');
  });

  it('styles the dialog from inside the shadow root, the page having no rules for it', async () => {
    open({ location: location() });
    await loaded();

    // Rules only export-popup.css states, checked as computed style: they prove the stylesheet is in effect
    // inside the root, not merely present as text.
    expect(getComputedStyle(shadow()!.querySelector('.property-wrapper')!).display).toBe('flex');
    expect(getComputedStyle(shadow()!.querySelector('.flex-column')!).flexBasis).toBe('320px');
  });

  it('marks a field the export was refused on, outranking the shared control styling', async () => {
    // The shared control system styles text inputs at a higher specificity than the legacy `.error` rule
    // had, which is why that rule never took effect. Asserted as computed style rather than as a class,
    // because the class is exactly what was there before and did nothing.
    open({ location: location() });
    await loaded();
    const chapters = await vi.waitFor(() => {
      const found = shadow()!.querySelector<HTMLInputElement>('#popup-chapters');
      expect(found).not.toBeNull();
      return found!;
    });

    await userEvent.fill(chapters, 'one, two');
    shadow()!.querySelector<HTMLButtonElement>('.rsp-modal-footer .sbb-btn--primary')!.click();

    // Polled rather than read once: the shared control system transitions its border color, so a single
    // read right after the class is set catches a shade partway to red.
    await vi.waitFor(() => {
      const marked = shadow()!.querySelector('#popup-chapters')!;
      expect(marked.className).toContain('error');
      expect(getComputedStyle(marked).borderColor).toBe('rgb(255, 0, 0)');
      expect(getComputedStyle(marked).color).toBe('rgb(255, 0, 0)');
    });
  });

  it('keeps the two settings columns side by side when a scrollbar takes width off the form', async () => {
    // Short enough that the form goes over its height cap and the content area scrolls. The scrollbar then
    // takes about 15px off it, leaving 685px where two fixed 340px columns and their 20px gap need 700 - so
    // a fixed width wrapped the right column underneath, which is how pdf-exporter's dialog shipped. The
    // columns are sized to shrink instead.
    //
    // The scrollbar is a real one, which the suite can only draw because vitest.config.ts passes
    // `ignoreDefaultArgs: ['--hide-scrollbars']`. Playwright hides scrollbars in headless Chromium by
    // default, and that is precisely why this class of defect reaches production with every test green.
    await page.viewport(900, 460);
    open({ location: location() });
    await loaded();

    const content = shadow()!.querySelector<HTMLElement>('.rsp-modal-content')!;
    expect(content.scrollHeight).toBeGreaterThan(content.clientHeight); // it really does scroll
    expect(content.offsetWidth - content.clientWidth).toBeGreaterThan(0); // and the scrollbar takes width

    const columns = Array.from(shadow()!.querySelectorAll<HTMLElement>('#popup-settings-columns > .flex-column'));
    expect(columns).toHaveLength(2);
    const [left, right] = columns.map((column) => column.getBoundingClientRect());
    expect(Math.round(left.top)).toBe(Math.round(right.top)); // same row, i.e. not wrapped
    expect(Math.round(left.width)).toBe(Math.round(right.width)); // and still equal
  });

  it('widens the dialog beyond what the shared modal caps itself at', async () => {
    // The shared Modal stops at min(640px, 100vw - 32px), which is narrower than this form's two columns.
    open({ location: location() });
    await loaded();

    const dialog = shadow()!.querySelector<HTMLElement>('.rsp-modal')!;
    expect(dialog.getBoundingClientRect().width).toBeGreaterThan(640);
  });

  it('may use the whole window height, not the share the shared modal allows itself', async () => {
    // The regression this guards: RSP's Modal caps the dialog at 85vh, which is 135px less than this at a
    // 900px window - enough to put a scrollbar on a form that had none on the page before. The cap here is
    // the window less the 16px margin the shared modal already keeps at its sides.
    await page.viewport(1280, 900);
    open({ location: location() });
    await loaded();

    const dialog = shadow()!.querySelector<HTMLElement>('.rsp-modal')!;
    expect(getComputedStyle(dialog).maxHeight).toBe(`${window.innerHeight - 32}px`);
    // Whatever the form's height, the dialog itself is never the scroller
    expect(scrollers(shadow()!)).not.toContain('rsp-modal');
  });

  it('scrolls its content and nothing else where the form does not fit', async () => {
    await page.viewport(1280, 460);
    open({ location: location() });
    await loaded();

    // Exactly one scrollbar, and on the content: the title and the buttons stay where they are
    expect(scrollers(shadow()!)).toEqual(['rsp-modal-content']);

    const header = shadow()!.querySelector('.rsp-modal-header')!.getBoundingClientRect();
    const footer = shadow()!.querySelector('.rsp-modal-footer')!.getBoundingClientRect();
    const content = shadow()!.querySelector('.rsp-modal-content')!;
    content.scrollTop = content.scrollHeight;

    expect(shadow()!.querySelector('.rsp-modal-header')!.getBoundingClientRect().top).toBe(header.top);
    expect(shadow()!.querySelector('.rsp-modal-footer')!.getBoundingClientRect().bottom).toBe(footer.bottom);
    // Both are on screen, which is the point of the dialog not being the scroller
    expect(header.top).toBeGreaterThanOrEqual(0);
    expect(footer.bottom).toBeLessThanOrEqual(window.innerHeight);
  });

  /** The dropdown option lists, wherever they currently live. */
  const portalsIn = (parent: ShadowRoot | Element): Element[] =>
    [...parent.children].filter((child) => child.classList.contains('sd-portal'));

  it('puts the dropdown option lists inside the dialog, where they are painted above it', async () => {
    // The regression this guards: the shared dropdown appends its `position: fixed` option list to the
    // element's root node - the shadow root, a *sibling* of the dialog. RSP's Modal is a native <dialog>
    // opened with showModal(), so it is in the browser's top layer and paints above anything in the normal
    // layer whatever its z-index. The list opened underneath the dialog, with only the part hanging past the
    // dialog's bottom edge visible.
    open({ location: location() });
    await loaded();

    const root = shadow()!;
    const dialog = root.querySelector<HTMLElement>('dialog.rsp-modal')!;

    expect(portalsIn(dialog).length).toBeGreaterThan(0);
    expect(portalsIn(root)).toEqual([]);
    // And the dialog must not clip them, or a list longer than the room below its trigger is cut off
    expect(getComputedStyle(dialog).overflow).toBe('visible');
  });

  it('adopts the option list of a dropdown that appears later', async () => {
    // The form grows dropdowns as it goes: switching the work item roles on mounts two SearchableSelects,
    // each creating its option list right then - which is why this is an observer and not a one-off pass.
    open({ location: location() });
    await loaded();
    const root = shadow()!;
    const dialog = root.querySelector<HTMLElement>('dialog.rsp-modal')!;
    await vi.waitFor(() => expect(root.querySelector('#popup-selected-roles')).not.toBeNull());
    // The sample package has the roles on, so they are switched off and on again to mount them afresh
    root.querySelector<HTMLInputElement>('#popup-selected-roles')!.click();
    await vi.waitFor(() => expect(root.querySelector('#popup-roles-selector')).toBeNull());
    const before = portalsIn(dialog).length;

    root.querySelector<HTMLInputElement>('#popup-selected-roles')!.click();

    await vi.waitFor(() => expect(root.querySelector('#popup-roles-selector')).not.toBeNull());
    await vi.waitFor(() => expect(portalsIn(dialog).length).toBe(before + 2));
    expect(portalsIn(root)).toEqual([]);
  });

  it('reads the document out of the page URL when it is not told where it is', async () => {
    window.history.replaceState({}, '', `${window.location.pathname}#/project/elibrary/wiki/Specs/BigDoc`);

    open();
    await loaded();

    // The file name request is what says the location was understood: it carries the path it read
    await vi.waitFor(() =>
      expect(shadow()!.querySelector<HTMLInputElement>('#popup-filename')?.value).toBe(
        'E-Library Cross Link Issue.docx',
      ),
    );
    const fetchMock = globalThis.fetch as unknown as { mock: { calls: [string, RequestInit?][] } };
    const call = fetchMock.mock.calls.find(([url]) => String(url).includes('export-filename'))!;
    const sent = JSON.parse(String(call[1]!.body)) as Record<string, unknown>;
    expect(sent).toMatchObject({ projectId: 'elibrary', locationPath: 'Specs/BigDoc' });
    // Every DOCX export is a Live Document, so the request says nothing about a type
    expect('documentType' in sent).toBe(false);
  });

  it('removes its host from the page when the dialog is closed', async () => {
    open({ location: location() });
    await loaded();
    const host = document.body.lastElementChild!;

    shadow()!.querySelector<HTMLButtonElement>('.rsp-modal-footer .sbb-btn--secondary')!.click();

    await vi.waitFor(() => expect(host.isConnected).toBe(false));
    // The root was unmounted with it, so the afterEach unmount is a no-op
    roots.length = 0;
  });

  it('closes a previously opened dialog instead of stacking a second one on top of it', async () => {
    // A second click on the toolbar button while the first dialog is still open must not leave two
    // independently submittable dialogs behind.
    const firstRoot = open({ location: location() });
    await loaded();
    const firstHost = document.body.lastElementChild!;

    open({ location: location() });
    await loaded();

    expect(firstHost.isConnected).toBe(false);
    expect([...document.querySelectorAll('body > div')].filter((element) => element.shadowRoot)).toHaveLength(1);
    // The first root was already unmounted by the second open() call
    roots.splice(roots.indexOf(firstRoot), 1);
  });
});
