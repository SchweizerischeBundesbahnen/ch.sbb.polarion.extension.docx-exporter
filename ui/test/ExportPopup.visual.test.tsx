import type { Root } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { page, userEvent } from 'vitest/browser';
import { openExportPopup } from '../src/popup/mount';
import { SAMPLE_DOCUMENT, popupDependencies } from './exportPopupSamples';
import type { PopupSampleOptions } from './exportPopupSamples';
import { SAMPLE_STYLE_PACKAGE, SAMPLE_STYLE_PACKAGE_FULL, SAMPLE_STYLE_PACKAGE_HIDDEN } from './sidePanelSamples';

// Docker-only snapshots of the "Export to DOCX" dialog as the editor toolbar button opens it, mounted the
// way openExportPopup mounts it: its own shadow root, carrying react-sbb-polarion's stylesheet, the base
// font rule and the dialog's own CSS. Polarion's page CSS is not part of this app and is not loaded here, so
// these references show the dialog's own styling - which is exactly what a change in this repo can move.
//
// The dialog is snapshotted rather than the page: it is a native <dialog> in the top layer, so an element
// screenshot of its host would be empty.

const roots: Root[] = [];

function mounted(options: PopupSampleOptions = {}): ShadowRoot {
  const root = openExportPopup({ location: SAMPLE_DOCUMENT, deps: popupDependencies(options) });
  roots.push(root);
  return (document.body.lastElementChild as HTMLElement).shadowRoot!;
}

const settled = (shadow: ShadowRoot, selector = '#popup-style-package-select') =>
  vi.waitFor(() => expect(shadow.querySelector(selector)).not.toBeNull());

/** Every dropdown painted and showing its selection, so a snapshot cannot catch a blank trigger. */
const dropdownsUpgraded = (shadow: ShadowRoot) =>
  vi.waitFor(() => {
    expect(shadow.querySelectorAll('.searchable-dropdown').length).toBe(shadow.querySelectorAll('select').length);
    const triggers = Array.from(shadow.querySelectorAll<HTMLInputElement>('input.sd-trigger'));
    expect(triggers.every((trigger) => trigger.value !== '')).toBe(true);
    const multi = Array.from(shadow.querySelectorAll('.sd-trigger-multi'));
    expect(multi.every((trigger) => trigger.querySelector('.sd-chip, .sd-placeholder') !== null)).toBe(true);
  });

/**
 * One viewport for every reference, rather than one derived from the dialog's height.
 *
 * The dialog caps itself at a share of the viewport, so a viewport measured from the dialog's own height is
 * circular and clips the tallest form. A viewport taller than any of these forms shows all of them whole,
 * and one shared value keeps the references comparable.
 */
const VIEWPORT = { width: 900, height: 1400 } as const;

/** Snapshots the dialog itself: it is a native <dialog> in the top layer, so its host's box is empty. */
async function snapshotDialog(shadow: ShadowRoot, name: string): Promise<void> {
  // Park the pointer somewhere without hover styling. Wherever it happened to rest after the previous test
  // might have some, which is enough to make a reference disagree with itself from one run to the next.
  await userEvent.hover(shadow.querySelector('.rsp-modal-title')!);
  await page.viewport(VIEWPORT.width, VIEWPORT.height);
  await expect(page.elementLocator(shadow.querySelector<HTMLElement>('.rsp-modal')!)).toMatchScreenshot(name);
}

async function snapshot(shadow: ShadowRoot, name: string): Promise<void> {
  await dropdownsUpgraded(shadow);
  await snapshotDialog(shadow, name);
}

afterEach(() => {
  roots.splice(0).forEach((root) => root.unmount());
  document.querySelectorAll('body > div').forEach((element) => {
    if (element.shadowRoot) element.remove();
  });
  document.cookie = 'selected-style-package=; path=/; max-age=0';
});

describe.skipIf(!__PIXEL_REFERENCES__)('export dialog visual', () => {
  it('a style package that exposes its settings', async () => {
    const shadow = mounted({ stylePackage: SAMPLE_STYLE_PACKAGE });
    await settled(shadow);

    await snapshot(shadow, 'popup-settings-exposed');
  });

  it('every optional setting switched on, which is the dialog at its tallest', async () => {
    const shadow = mounted({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL, data: { webhooksEnabled: true } });
    await settled(shadow);

    await snapshot(shadow, 'popup-everything-on');
  });

  it('a style package that keeps its settings to itself', async () => {
    const shadow = mounted({ stylePackage: SAMPLE_STYLE_PACKAGE_HIDDEN });
    await settled(shadow);

    await snapshot(shadow, 'popup-settings-hidden');
  });

  it('an export in progress, with the form out of reach', async () => {
    // The sample conversion never completes, which is the in-progress state
    const shadow = mounted();
    await settled(shadow);
    await dropdownsUpgraded(shadow);

    shadow.querySelector<HTMLButtonElement>('.rsp-modal-footer .sbb-btn--primary')!.click();
    await vi.waitFor(() => expect(shadow.querySelector('.in-progress-overlay.show')).not.toBeNull());

    await snapshot(shadow, 'popup-exporting');
  });

  it('a field the export was refused on', async () => {
    const shadow = mounted({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL });
    await settled(shadow);
    await dropdownsUpgraded(shadow);

    await userEvent.fill(shadow.querySelector<HTMLInputElement>('#popup-chapters')!, 'one, two');
    shadow.querySelector<HTMLButtonElement>('.rsp-modal-footer .sbb-btn--primary')!.click();
    await vi.waitFor(() => expect(shadow.querySelector('.notifications .alert-error')).not.toBeNull());

    await snapshot(shadow, 'popup-invalid-field');
  });

  it('an open dropdown, which has to paint above the dialog', async () => {
    // The option list is a `position: fixed` portal the shared dropdown creates outside the React tree, and
    // the dialog is in the browser's top layer - so the list is moved into the dialog to be painted above it
    // at all (see popup/dialogPortals.ts). It is a child of the dialog, so this element screenshot shows it.
    const shadow = mounted({ stylePackage: SAMPLE_STYLE_PACKAGE });
    await settled(shadow);
    await dropdownsUpgraded(shadow);

    const trigger = shadow
      .querySelector('#popup-template-selector')!
      .closest('.property-wrapper')!
      .querySelector<HTMLInputElement>('input.sd-trigger')!;
    // The shared dropdown opens on mousedown, not on click
    trigger.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await vi.waitFor(() => {
      const list = shadow.querySelector<HTMLElement>('.sd-portal[style*="block"] .options');
      expect(list).not.toBeNull();
      expect(list!.querySelectorAll('.option').length).toBeGreaterThan(0);
    });

    await snapshotDialog(shadow, 'popup-dropdown-open');
  });

  it('a short window, where the scrollbar must not push the second column away', async () => {
    // The one reference not taken at VIEWPORT, and the only one that reproduces the defect pdf-exporter's
    // dialog actually shipped with: in a real Polarion the two columns wrapped into a single tall column.
    //
    // The window is a normal width and merely short. That is enough: the form goes over its height cap, the
    // content area scrolls, and the scrollbar takes about 15px off it - leaving 685px where two fixed 340px
    // columns and their 20px gap need 700. The columns are sized to shrink rather than wrap (see
    // .flex-column in export-popup.css), so they stay level here.
    //
    // The scrollbar is real, not simulated. It needs `ignoreDefaultArgs: ['--hide-scrollbars']` in
    // vitest.config.ts: Playwright passes that flag to headless Chromium by default, which is why this
    // whole class of defect is invisible to a suite that does not ask for it.
    const shadow = mounted({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL, data: { webhooksEnabled: true } });
    await settled(shadow);
    await dropdownsUpgraded(shadow);
    await page.viewport(900, 460);

    await expect(page.elementLocator(shadow.querySelector<HTMLElement>('.rsp-modal')!)).toMatchScreenshot(
      'popup-small-window',
    );
  });

  it('the data it could not read', async () => {
    const shadow = mounted({ loadError: new Error("No 'templates' configurations in scope 'project/elibrary/'") });
    await vi.waitFor(() => expect(shadow.querySelector('.notifications .alert-error')).not.toBeNull());

    await snapshotDialog(shadow, 'popup-load-failed');
  });
});
