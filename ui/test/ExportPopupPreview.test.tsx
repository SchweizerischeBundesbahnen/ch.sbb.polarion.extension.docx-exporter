import { pageViolations } from '@sbb-polarion/react-sbb-polarion/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import ExportPopupPreview from '../src/pages/ExportPopupPreview';
import { dropdownsSettled } from './a11yHelpers';
import { popupRoutes } from './exportPopupSamples';
import { installFetchMock } from './mockFetch';
import type { Route } from './mockFetch';

// The export dialog's development harness. It is dev-only scaffolding, but the part that decides what the
// dialog is pointed at is not: the editor hash it writes for the picked document is what
// services/exportContext.ts parses, so a wrong one means the harness silently exercises the wrong document.

const DOCUMENTS = [{ attributes: { moduleFolder: 'Default Space', moduleName: 'Cross Link Issue' } }];

const routes = (): Route[] => [
  { method: 'GET', match: /\/projects\/[^/]+\/documents/, json: { data: DOCUMENTS } },
  ...popupRoutes(),
];

const open = () => {
  installFetchMock(routes());
  window.history.replaceState({}, '', '?feature=export-popup&scope=project%2Felibrary%2F');
  render(<ExportPopupPreview />);
};

const select = () => document.querySelector<HTMLSelectElement>('#dev-document-select');
const openButton = () => document.querySelector<HTMLButtonElement>('.preview-controls button')!;

const documentsLoaded = () => vi.waitFor(() => expect(select()?.querySelectorAll('option').length).toBeGreaterThan(1));

/**
 * The dialog's shadow root, once the dialog has rendered in it. It is mounted on a host of its own appended to
 * the body (see popup/mount.tsx), but not the body's last child for long: the dropdown portals follow it.
 *
 * Waits for the root itself. The harness opens the dialog through a dynamic import, so right after the click
 * there is no host yet. A wait that accepts `undefined` passes at once, and the dialog then opens during the
 * next test and covers its page.
 */
const dialogRoot = () =>
  vi.waitFor(() => {
    const root = Array.from(document.querySelectorAll('body > div'))
      .map((element) => element.shadowRoot)
      .find((candidate) => candidate?.querySelector('#popup-style-package-select'));
    expect(root).toBeDefined();
    return root!;
  });

const pick = (value: string) => {
  const element = select()!;
  element.value = value;
  element.dispatchEvent(new Event('change', { bubbles: true }));
};

beforeEach(() => {
  // A leftover selection from another test would preselect a document unasked.
  document.cookie = 'docx-exporter-dev-document=; path=/; max-age=0';
});

afterEach(() => {
  // Closed the way the user closes it, which unmounts the dialog's React root and removes its host. Removing
  // the host alone would leave that root mounted, and openExportPopup's handle to it set.
  document.querySelectorAll('body > div').forEach((element) => {
    element.shadowRoot?.querySelector<HTMLButtonElement>('.rsp-modal-footer .sbb-btn--secondary')?.click();
  });
  cleanup();
  document.querySelectorAll('body > div').forEach((element) => {
    if (element.shadowRoot) element.remove();
  });
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', window.location.pathname);
  document.cookie = 'selected-style-package=; path=/; max-age=0';
});

describe('the export dialog development harness', () => {
  it('offers no dialog until a document is picked', async () => {
    open();
    await documentsLoaded();

    expect(openButton().disabled).toBe(true);
  });

  it('writes the editor hash of the picked document and opens the real dialog on it', async () => {
    open();
    await documentsLoaded();
    pick('Default Space/Cross Link Issue');

    await vi.waitFor(() => expect(openButton().disabled).toBe(false));
    await userEvent.click(openButton());

    // The hash a real editor would have, which is what the export context reads
    await vi.waitFor(() =>
      expect(window.location.hash).toBe('#/project/elibrary/wiki/Default%20Space/Cross%20Link%20Issue'),
    );
    // Opened for real: its own shadow root, not markup in the page
    await dialogRoot();
    // `?feature=` survives, the app routing on the search parameters rather than the hash
    expect(window.location.search).toContain('feature=export-popup');
  });
});

describe('accessibility', () => {
  it('has no WCAG A/AA violations before a document is picked', async () => {
    open();
    await documentsLoaded();
    await dropdownsSettled();
    expect(await pageViolations()).toEqual([]);
  });

  it('has no WCAG A/AA violations with the dialog open', async () => {
    open();
    await documentsLoaded();
    pick('Default Space/Cross Link Issue');
    await vi.waitFor(() => expect(openButton().disabled).toBe(false));
    await userEvent.click(openButton());
    await dropdownsSettled(await dialogRoot());
    expect(await pageViolations()).toEqual([]);
  });
});
