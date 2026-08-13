import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import ExportPopupModal from '../src/popup/ExportPopupModal';
import type { ExportPopupDependencies } from '../src/popup/ExportPopupModal';
import type { DocumentIdentity } from '../src/services/exportContext';
import { SAMPLE_DOCUMENT, SAMPLE_POPUP_DATA, docxResult, popupDependencies } from './exportPopupSamples';
import { SAMPLE_STYLE_PACKAGE_FULL, SAMPLE_STYLE_PACKAGE_HIDDEN } from './sidePanelSamples';

// The "Export to DOCX" dialog the document editor toolbar button opens: what the selected style package puts
// on screen, what the export sends, and what the user is told when something is wrong.
//
// The dialog is rendered directly rather than through `openExportPopup`, so the assertions read the document
// rather than a shadow root; the mounting itself is covered by ExportPopupMount.test.tsx. Its REST data and
// its conversion are replaced (see exportPopupSamples): a browser test has neither a Polarion to read from
// nor a page to be on.

interface OpenOptions {
  document?: DocumentIdentity;
  onClose?: () => void;
  deps?: ExportPopupDependencies;
}

const open = (options: OpenOptions = {}) =>
  render(
    <ExportPopupModal
      document={options.document ?? SAMPLE_DOCUMENT}
      onClose={options.onClose ?? (() => {})}
      deps={options.deps ?? popupDependencies()}
    />,
  );

const field = <T extends HTMLElement>(selector: string) => document.querySelector<T>(selector);
const checkbox = (id: string) => field<HTMLInputElement>(`#${id}`)!;
const value = (id: string) => field<HTMLInputElement>(`#${id}`)!.value;
const text = (selector: string) => field(selector)?.textContent ?? '';
const selected = (id: string) => field<HTMLSelectElement>(`#${id}`)?.value;

const exportButton = () => field<HTMLButtonElement>('.rsp-modal-footer .sbb-btn--primary')!;
const closeButton = () => field<HTMLButtonElement>('.rsp-modal-footer .sbb-btn--secondary')!;

/** Waits for the dialog to have loaded its data and its style package. */
const settled = () => vi.waitFor(() => expect(field('#popup-style-package-select')).not.toBeNull());

/** Waits for the settings block, which only a package that exposes its settings puts on screen. */
const settledWithSettings = () => vi.waitFor(() => expect(field('#popup-style-package-content')).not.toBeNull());

/** Drives a SearchableSelect by the native select it wraps, which is its source of truth. */
const choose = (id: string, chosen: string) => {
  const select = field<HTMLSelectElement>(`#${id}`)!;
  select.value = chosen;
  select.dispatchEvent(new Event('change', { bubbles: true }));
};

beforeEach(() => {
  // The style package the dialog remembers is a cookie; each test starts without one.
  document.cookie = 'selected-style-package=; path=/; max-age=0';
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  document.cookie = 'selected-style-package=; path=/; max-age=0';
});

describe('what the style package puts on screen', () => {
  it('offers the settings a package exposes', async () => {
    open();
    await settledWithSettings();

    expect(text('#popup-style-package-content')).toContain('exposes its settings');
    expect(field('#popup-template-selector')).not.toBeNull();
    expect(field('#popup-removal-selector')).not.toBeNull();
  });

  it('offers nothing but the name and the file name for a package that exposes none', async () => {
    open({ deps: popupDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_HIDDEN }) });
    await settled();

    expect(field('#popup-style-package-content')).toBeNull();
    expect(field('#popup-filename')).not.toBeNull();
  });

  it('preselects the first suitable style package, which the server ordered by weight', async () => {
    open();
    await settled();

    expect(selected('popup-style-package-select')).toBe(SAMPLE_POPUP_DATA.stylePackages[0].id);
  });

  it('offers the package the user picked last, and remembers a new pick', async () => {
    document.cookie = 'selected-style-package=Specification; path=/';
    open();
    await settled();

    expect(selected('popup-style-package-select')).toBe('Specification');

    choose('popup-style-package-select', 'Default');
    await vi.waitFor(() => expect(document.cookie).toContain('selected-style-package=Default'));
  });

  it('ignores a remembered package the document no longer allows', async () => {
    document.cookie = 'selected-style-package=Gone; path=/';
    open();
    await settled();

    expect(selected('popup-style-package-select')).toBe('Default');
  });

  it('sets every control from the package it loaded', async () => {
    open({ deps: popupDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL }) });
    await settledWithSettings();

    expect(selected('popup-template-selector')).toBe('Default');
    expect(selected('popup-orientation-selector')).toBe('PORTRAIT');
    expect(selected('popup-paper-size-selector')).toBe('A4');
    expect(selected('popup-image-density-selector')).toBe('DPI_96');
    expect(checkbox('popup-preserve-table-styles').checked).toBe(true);
    expect(checkbox('popup-cut-empty-chapters').checked).toBe(true);
    expect(value('popup-chapters')).toBe('1,2');
    expect(value('popup-removal-selector')).toBe('img.decorative');
  });

  it('leaves the three page setup rows off, so the reference template decides them', async () => {
    // The DOCX-specific rows: a package that overrides none of them sends none of them.
    open();
    await settledWithSettings();

    expect(checkbox('popup-orientation').checked).toBe(false);
    expect(field('#popup-orientation-selector')).toBeNull();
    expect(field('#popup-paper-size-selector')).toBeNull();
    expect(field('#popup-image-density-selector')).toBeNull();
  });

  it('reveals a page setup dropdown when its row is switched on', async () => {
    open();
    await settledWithSettings();

    await userEvent.click(checkbox('popup-orientation'));

    await vi.waitFor(() => expect(field('#popup-orientation-selector')).not.toBeNull());
    expect(selected('popup-orientation-selector')).toBe('PORTRAIT');
  });

  it('reserves the space of a value field rather than removing it, as the legacy popup did', async () => {
    // `visibility` and not `display`: ticking a checkbox must not reflow the column around it.
    open();
    await settledWithSettings();

    expect(getComputedStyle(field('#popup-chapters')!).visibility).toBe('hidden');

    await userEvent.click(checkbox('popup-specific-chapters'));
    expect(getComputedStyle(field('#popup-chapters')!).visibility).toBe('visible');
  });

  it('shows the comment option only while comments are rendered', async () => {
    open();
    await settledWithSettings();

    expect(field('#popup-include-unreferenced-comments')).toBeNull();

    await userEvent.click(checkbox('popup-render-comments'));

    expect(field('#popup-include-unreferenced-comments')).not.toBeNull();
  });

  it('hides the webhooks row where the installation has webhooks switched off', async () => {
    open({ deps: popupDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL }) });
    await settledWithSettings();

    expect(field('#popup-webhooks-checkbox')).toBeNull();
  });

  it('offers the webhooks row where the installation has them switched on', async () => {
    open({ deps: popupDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL, data: { webhooksEnabled: true } }) });
    await settledWithSettings();

    expect(checkbox('popup-webhooks-checkbox').checked).toBe(true);
    expect(selected('popup-webhooks-selector')).toBe('Default');
  });

  it('hides the roles group where the project defines no link roles', async () => {
    open({ deps: popupDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL, data: { roles: [] } }) });
    await settledWithSettings();

    expect(field('#popup-selected-roles')).toBeNull();
  });

  it('offers the roles and their direction once the roles are switched on', async () => {
    open({ deps: popupDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL }) });
    await settledWithSettings();

    expect(checkbox('popup-selected-roles').checked).toBe(true);
    expect(field('#popup-roles-selector')).not.toBeNull();
    expect(selected('popup-roles-direction-selector')).toBe('BOTH');
  });

  it('offers the language the document is written in, over the one the package names', async () => {
    open({
      deps: popupDependencies({
        stylePackage: { ...SAMPLE_STYLE_PACKAGE_FULL, language: 'fr' },
        data: { documentLanguage: 'it' },
      }),
    });
    await settledWithSettings();

    expect(selected('popup-language')).toBe('it');
  });

  it('reloads every field when another style package is picked', async () => {
    const packages: Record<string, typeof SAMPLE_STYLE_PACKAGE_FULL> = {
      Default: SAMPLE_STYLE_PACKAGE_FULL,
      Specification: { ...SAMPLE_STYLE_PACKAGE_FULL, paperSize: 'A3', preserveTableStyles: false },
    };
    open({
      deps: {
        ...popupDependencies(),
        loadPackage: (_send, name) => Promise.resolve(packages[name] ?? SAMPLE_STYLE_PACKAGE_FULL),
      },
    });
    await settledWithSettings();
    expect(selected('popup-paper-size-selector')).toBe('A4');

    choose('popup-style-package-select', 'Specification');

    await vi.waitFor(() => expect(selected('popup-paper-size-selector')).toBe('A3'));
    expect(checkbox('popup-preserve-table-styles').checked).toBe(false);
  });
});

describe('exporting', () => {
  it('sends what the form says and downloads the result under the file name shown', async () => {
    const requests: string[] = [];
    const downloads: string[] = [];
    open({
      deps: popupDependencies({
        stylePackage: SAMPLE_STYLE_PACKAGE_FULL,
        convert: (request) => {
          requests.push(request);
          return Promise.resolve(docxResult());
        },
        download: (_blob, name) => downloads.push(name),
      }),
    });
    await settledWithSettings();

    await userEvent.click(exportButton());

    await vi.waitFor(() => expect(downloads).toEqual(['E-Library Cross Link Issue.docx']));
    const sent = JSON.parse(requests[0]) as Record<string, unknown>;
    expect(sent.paperSize).toBe('A4');
    expect(sent.chapters).toEqual(['1', '2']);
    expect(sent.fileName).toBe('E-Library Cross Link Issue.docx');
    // Every DOCX export is a Live Document, and ExportParams.java has no field for a type
    expect('documentType' in sent).toBe(false);
    expect(text('.notifications .alert-success')).toBe('DOCX was successfully generated');
  });

  it('carries every switch and every typed value into the export', async () => {
    // One pass over the whole form: each control is driven the way a user drives it, and what the export
    // then sends is what says the control is wired to the request rather than only to the screen.
    const requests: string[] = [];
    open({
      deps: popupDependencies({
        data: { webhooksEnabled: true },
        convert: (request) => {
          requests.push(request);
          return Promise.resolve(docxResult());
        },
      }),
    });
    await settledWithSettings();

    for (const id of [
      'popup-webhooks-checkbox',
      'popup-orientation',
      'popup-paper-size',
      'popup-image-density',
      'popup-preserve-table-styles',
      'popup-render-comments',
      'popup-cut-empty-chapters',
      'popup-cut-empty-wi-attributes',
      'popup-cut-urls',
      'popup-specific-chapters',
      'popup-localization',
      'popup-selected-roles',
    ]) {
      await userEvent.click(checkbox(id));
    }
    // The comment option only exists once comments are rendered, which the sweep above switched on
    await userEvent.click(checkbox('popup-include-unreferenced-comments'));

    await userEvent.fill(field<HTMLInputElement>('#popup-chapters')!, '3');
    await userEvent.fill(field<HTMLInputElement>('#popup-removal-selector')!, 'table.unwanted');
    for (const [id, chosen] of [
      ['popup-template-selector', 'SBB'],
      ['popup-localization-selector', 'SBB'],
      ['popup-webhooks-selector', 'SBB'],
      ['popup-orientation-selector', 'LANDSCAPE'],
      ['popup-paper-size-selector', 'A3'],
      ['popup-image-density-selector', 'DPI_300'],
      ['popup-render-comments-selector', 'ALL'],
      ['popup-language', 'it'],
      ['popup-roles-direction-selector', 'REVERSE'],
    ]) {
      choose(id, chosen);
    }
    const roles = field<HTMLSelectElement>('#popup-roles-selector')!;
    Array.from(roles.options).forEach((option) => (option.selected = option.value !== 'depends_on'));
    roles.dispatchEvent(new Event('change', { bubbles: true }));

    await userEvent.click(exportButton());

    await vi.waitFor(() => expect(requests).toHaveLength(1));
    const sent = JSON.parse(requests[0]) as Record<string, unknown>;
    expect(sent.template).toBe('SBB');
    expect(sent.localization).toBe('SBB');
    expect(sent.webhooks).toBe('SBB');
    expect(sent.orientation).toBe('LANDSCAPE');
    expect(sent.paperSize).toBe('A3');
    expect(sent.imageDensity).toBe('DPI_300');
    expect(sent.preserveTableStyles).toBe(true);
    expect(sent.renderComments).toBe('ALL');
    expect(sent.includeUnreferencedComments).toBe(true);
    expect(sent.cutEmptyChapters).toBe(true);
    // The package had this one on, so a click turns it off
    expect(sent.cutEmptyWIAttributes).toBe(false);
    expect(sent.cutLocalUrls).toBe(true);
    expect(sent.chapters).toEqual(['3']);
    expect(sent.language).toBe('it');
    expect(sent.linkedWorkitemRoles).toEqual(['relates_to', 'verifies']);
    expect(sent.linkRoleDirection).toBe('REVERSE');
    expect(sent.removalSelector).toBe('table.unwanted');
  });

  it('leaves out what is switched off, so the reference template decides it', async () => {
    // A null field is dropped from the body entirely - see toRequestBody - which is not the same to the
    // server as a value the control happens to be showing.
    const requests: string[] = [];
    open({
      deps: popupDependencies({
        stylePackage: SAMPLE_STYLE_PACKAGE_FULL,
        data: { webhooksEnabled: true },
        convert: (request) => {
          requests.push(request);
          return Promise.resolve(docxResult());
        },
      }),
    });
    await settledWithSettings();

    for (const id of [
      'popup-webhooks-checkbox',
      'popup-orientation',
      'popup-paper-size',
      'popup-image-density',
      'popup-render-comments',
      'popup-specific-chapters',
      'popup-localization',
      'popup-selected-roles',
    ]) {
      await userEvent.click(checkbox(id));
    }

    await userEvent.click(exportButton());

    await vi.waitFor(() => expect(requests).toHaveLength(1));
    const sent = JSON.parse(requests[0]) as Record<string, unknown>;
    for (const key of [
      'webhooks',
      'orientation',
      'paperSize',
      'imageDensity',
      'renderComments',
      'chapters',
      'language',
      'linkRoleDirection',
    ]) {
      expect(key in sent, key).toBe(false);
    }
    expect(sent.includeUnreferencedComments).toBe(false);
    expect(sent.linkedWorkitemRoles).toEqual([]);
  });

  it('appends .docx to a name the user typed without it', async () => {
    const downloads: string[] = [];
    open({
      deps: popupDependencies({
        convert: () => Promise.resolve(docxResult()),
        download: (_blob, name) => downloads.push(name),
      }),
    });
    await settled();

    await userEvent.fill(field<HTMLInputElement>('#popup-filename')!, 'My Export');
    await userEvent.click(exportButton());

    await vi.waitFor(() => expect(downloads).toEqual(['My Export.docx']));
  });

  it('falls back to the default name when the user cleared the field', async () => {
    const downloads: string[] = [];
    open({
      deps: popupDependencies({
        convert: () => Promise.resolve(docxResult()),
        download: (_blob, name) => downloads.push(name),
      }),
    });
    await settled();

    await userEvent.clear(field<HTMLInputElement>('#popup-filename')!);
    await userEvent.click(exportButton());

    await vi.waitFor(() => expect(downloads).toEqual(['E-Library Cross Link Issue.docx']));
  });

  it('shows the warning a conversion came back with, as text rather than markup', async () => {
    open({ deps: popupDependencies({ convert: () => Promise.resolve(docxResult('One image\n\nwas not exported')) }) });
    await settled();

    await userEvent.click(exportButton());

    await vi.waitFor(() => expect(text('.notifications .alert-warning')).toBe('One image\n\nwas not exported'));
  });

  it('shows why a conversion failed', async () => {
    open({ deps: popupDependencies({ convert: () => Promise.reject(new Error('The document has no content')) }) });
    await settled();

    await userEvent.click(exportButton());

    await vi.waitFor(() =>
      expect(text('.notifications .alert-error')).toBe(
        'Error occurred during DOCX generation: The document has no content',
      ),
    );
  });

  it('says only that it failed when the server gave no reason', async () => {
    open({ deps: popupDependencies({ convert: () => Promise.reject(new Error('')) }) });
    await settled();

    await userEvent.click(exportButton());

    await vi.waitFor(() => expect(text('.notifications .alert-error')).toBe('Error occurred during DOCX generation'));
  });

  it('covers the form and disables the Export button while an export runs', async () => {
    // The sample conversion never completes, which is the in-progress state
    open();
    await settled();

    await userEvent.click(exportButton());

    await vi.waitFor(() => expect(field('.in-progress-overlay.show')).not.toBeNull());
    expect(text('#in-progress-message')).toBe('Generating DOCX');
    expect(exportButton().disabled).toBe(true);
  });

  it('refuses to export on a bad chapters entry, and marks the field', async () => {
    open({ deps: popupDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL }) });
    await settledWithSettings();

    await userEvent.fill(field<HTMLInputElement>('#popup-chapters')!, 'one, two');
    await userEvent.click(exportButton());

    expect(text('.notifications .alert-error')).toContain('comma separated list of integer values');
    expect(field('#popup-chapters')!.className).toContain('error');
    // Nothing was started, so the dialog is still usable
    expect(exportButton().disabled).toBe(false);
  });

  it('closes when the user asks', async () => {
    const closes: number[] = [];
    open({ onClose: () => closes.push(1) });
    await settled();

    await userEvent.click(closeButton());

    expect(closes).toHaveLength(1);
  });
});

describe('what the dialog says when it cannot load', () => {
  it('reports the data it could not read, and refuses to export', async () => {
    open({
      deps: popupDependencies({ loadError: new Error("No 'templates' configurations in scope 'project/elibrary/'") }),
    });

    await vi.waitFor(() => expect(text('.notifications .alert-error')).toContain('Error occurred loading form data'));
    expect(text('.notifications .alert-error')).toContain("No 'templates' configurations");
    expect(exportButton().disabled).toBe(true);
    expect(field('.in-progress-overlay.show')).toBeNull();
  });

  it('reports a style package that cannot be read', async () => {
    open({ deps: { ...popupDependencies(), loadPackage: () => Promise.reject(new Error('HTTP 500')) } });

    await vi.waitFor(() =>
      expect(text('.notifications .alert-error')).toBe('Error occurred loading style package data: HTTP 500'),
    );
  });
});
