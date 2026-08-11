import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import type { StylePackageSettings } from '../src/services/stylePackage';
import SidePanel from '../src/sidepanel/SidePanel';
import type { SidePanelDependencies } from '../src/sidepanel/SidePanel';
import {
  SAMPLE_PANEL_DATA,
  SAMPLE_STYLE_PACKAGE,
  SAMPLE_STYLE_PACKAGE_FULL,
  SAMPLE_STYLE_PACKAGE_HIDDEN,
  docxResult,
  sampleDependencies,
} from './sidePanelSamples';

// The export panel of the document editor: what the selected style package puts on screen, what the export
// sends, and what the user is told when something is wrong. The panel is rendered directly rather than
// through `mountSidePanel` so the assertions read the document rather than a shadow root; the mounting
// itself is covered by SidePanelMount.test.tsx.
//
// The document location, the conversion and the REST data are replaced (see sidePanelSamples): a browser
// test has neither a Polarion to read from nor an editor URL to be in.

const open = (deps: SidePanelDependencies = sampleDependencies()) => render(<SidePanel deps={deps} />);

/** Waits for the panel to have loaded its data and its style package. */
const settled = () => vi.waitFor(() => expect(document.querySelector('#filename')).not.toBeNull());

const field = <T extends HTMLElement>(selector: string) => document.querySelector<T>(selector);
const checkbox = (id: string) => field<HTMLInputElement>(`#${id}`)!;
const text = (selector: string) => field(selector)?.textContent ?? '';

/** The panel's dropdowns are SearchableSelects; their value is the native select they wrap. */
const selected = (id: string) => field<HTMLSelectElement>(`#${id}`)?.value;

/** Drives a dropdown the way a user does: the SearchableSelect mirrors the native select it wraps. */
function pick(id: string, value: string): void {
  const select = field<HTMLSelectElement>(`#${id}`)!;
  select.value = value;
  select.dispatchEvent(new Event('change', { bubbles: true }));
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('what the style package puts on screen', () => {
  it('offers the settings a package exposes', async () => {
    open();
    await settled();

    expect(field('#style-package-content')).not.toBeNull();
    expect(text('#style-package-content')).toContain('exposes its settings');
  });

  it('offers nothing but the name and the button for a package that exposes none', async () => {
    open(sampleDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_HIDDEN }));
    await settled();

    expect(field('#style-package-content')).toBeNull();
    expect(field('#filename')).not.toBeNull();
    expect(field('#export-docx')).not.toBeNull();
  });

  it('preselects the first suitable style package, which the server ordered by weight', async () => {
    open();
    await settled();

    expect(selected('style-package-select')).toBe(SAMPLE_PANEL_DATA.stylePackages[0].id);
  });

  it('sets every control from the package it loaded', async () => {
    open(sampleDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL }));
    await settled();

    expect(selected('template-selector')).toBe('Default');
    expect(selected('paper-size-selector')).toBe('A4');
    expect(selected('orientation-selector')).toBe('PORTRAIT');
    expect(selected('image-density-selector')).toBe('DPI_96');
    expect(checkbox('preserve-table-styles').checked).toBe(true);
    expect(checkbox('cut-empty-chapters').checked).toBe(true);
    expect(field<HTMLInputElement>('#chapters')!.value).toBe('1,2');
    expect(field<HTMLInputElement>('#removal-selector')!.value).toBe('img.decorative');
  });

  it('leaves the page setup dropdowns off for a package that overrides none of them', async () => {
    // The DOCX-specific shape: nothing stored means "take what the reference template says", which is a
    // cleared checkbox and no dropdown at all - not a dropdown showing a default.
    open();
    await settled();

    expect(checkbox('orientation').checked).toBe(false);
    expect(checkbox('paper-size').checked).toBe(false);
    expect(checkbox('image-density').checked).toBe(false);
    expect(field('#orientation-selector')).toBeNull();
    expect(field('#paper-size-selector')).toBeNull();
    expect(field('#image-density-selector')).toBeNull();
  });

  it('shows a value field only while its switch is on', async () => {
    open();
    await settled();

    expect(field('#chapters')).toBeNull();
    await userEvent.click(checkbox('specific-chapters'));
    expect(field('#chapters')).not.toBeNull();

    await userEvent.click(checkbox('specific-chapters'));
    expect(field('#chapters')).toBeNull();
  });

  it('keeps the value a switch was turned off on, so turning it back on restores it', async () => {
    open(sampleDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL }));
    await settled();

    pick('paper-size-selector', 'A3');
    await userEvent.click(checkbox('paper-size'));
    await userEvent.click(checkbox('paper-size'));

    expect(selected('paper-size-selector')).toBe('A3');
  });

  it('shows the comment options only while comments are rendered', async () => {
    open();
    await settled();

    expect(field('#render-comments-options')).toBeNull();
    await userEvent.click(checkbox('render-comments'));

    expect(field('#render-comments-selector')).not.toBeNull();
    expect(field('#include-unreferenced-comments')).not.toBeNull();
  });

  it('hides the webhooks row where the installation has webhooks switched off', async () => {
    open(sampleDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL }));
    await settled();

    expect(field('#webhooks-checkbox')).toBeNull();
  });

  it('offers the webhooks row where the installation has them switched on', async () => {
    open(sampleDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL, data: { webhooksEnabled: true } }));
    await settled();

    expect(field('#webhooks-checkbox')).not.toBeNull();
    expect(selected('webhooks-selector')).toBe('Default');
  });

  it('hides the roles group where the project defines no link roles', async () => {
    open(sampleDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL, data: { roles: [] } }));
    await settled();

    expect(field('.roles-fields')).toBeNull();
  });

  it('offers the roles and their direction once the roles are switched on', async () => {
    open(sampleDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL }));
    await settled();

    expect(checkbox('selected-roles').checked).toBe(true);
    expect(field('#roles-selector')).not.toBeNull();
    expect(selected('roles-direction-selector')).toBe('BOTH');
  });

  it('offers the document language rather than the package language where the settings are exposed', async () => {
    // The sample document's docLanguage is "de"; the package asks for Italian and loses.
    open(sampleDependencies({ stylePackage: { ...SAMPLE_STYLE_PACKAGE_FULL, language: 'it' } }));
    await settled();

    expect(selected('language')).toBe('de');
  });

  it('carries every switch and every typed value into the export', async () => {
    // One pass over the whole form: each control is driven the way a user drives it, and what the export
    // then sends is what says the control is wired to the request rather than only to the screen.
    const requests: string[] = [];
    open(
      sampleDependencies({
        convert: (request) => {
          requests.push(request);
          return Promise.resolve(docxResult());
        },
      }),
    );
    await settled();

    for (const id of [
      'orientation',
      'paper-size',
      'image-density',
      'preserve-table-styles',
      'cut-empty-chapters',
      'cut-empty-wi-attributes',
      'cut-urls',
      'specific-chapters',
      'localization',
      'selected-roles',
    ]) {
      await userEvent.click(checkbox(id));
    }
    await userEvent.fill(field<HTMLInputElement>('#chapters')!, '3');
    await userEvent.fill(field<HTMLInputElement>('#removal-selector')!, 'table.unwanted');

    await userEvent.click(field<HTMLButtonElement>('#export-docx')!);

    const sent = JSON.parse(requests[0]) as Record<string, unknown>;
    // Switched on with the defaults still showing
    expect(sent.orientation).toBe('PORTRAIT');
    expect(sent.paperSize).toBe('A4');
    expect(sent.imageDensity).toBe('DPI_96');
    expect(sent.preserveTableStyles).toBe(true);
    expect(sent.cutEmptyChapters).toBe(true);
    // The package had this one on, so a click turns it off
    expect(sent.cutEmptyWIAttributes).toBe(false);
    expect(sent.cutLocalUrls).toBe(true);
    expect(sent.chapters).toEqual(['3']);
    expect(sent.language).toBe('de');
    expect(sent.removalSelector).toBe('table.unwanted');
    // Switched on with nothing picked yet: the roles group is offered, the request carries no role
    expect(sent.linkedWorkitemRoles).toEqual([]);
    // A DOCX export is always a Live Document, and ExportParams.java has no field to say so
    expect(sent.documentType).toBeUndefined();
  });

  it('leaves the page setup out of the export where its switches are off', async () => {
    const requests: string[] = [];
    open(
      sampleDependencies({
        convert: (request) => {
          requests.push(request);
          return Promise.resolve(docxResult());
        },
      }),
    );
    await settled();

    await userEvent.click(field<HTMLButtonElement>('#export-docx')!);

    const sent = JSON.parse(requests[0]) as Record<string, unknown>;
    expect('orientation' in sent).toBe(false);
    expect('paperSize' in sent).toBe(false);
    expect('imageDensity' in sent).toBe(false);
  });

  it('drives the dropdowns into the export', async () => {
    const requests: string[] = [];
    open(
      sampleDependencies({
        stylePackage: SAMPLE_STYLE_PACKAGE_FULL,
        convert: (request) => {
          requests.push(request);
          return Promise.resolve(docxResult());
        },
      }),
    );
    await settled();

    pick('paper-size-selector', 'A3');
    pick('orientation-selector', 'LANDSCAPE');
    pick('image-density-selector', 'DPI_300');
    pick('template-selector', 'SBB');
    pick('localization-selector', 'SBB');

    await userEvent.click(field<HTMLButtonElement>('#export-docx')!);

    const sent = JSON.parse(requests[0]) as Record<string, unknown>;
    expect(sent.paperSize).toBe('A3');
    expect(sent.orientation).toBe('LANDSCAPE');
    expect(sent.imageDensity).toBe('DPI_300');
    expect(sent.template).toBe('SBB');
    expect(sent.localization).toBe('SBB');
  });

  it('carries the comment options and the roles into the export', async () => {
    const requests: string[] = [];
    open(
      sampleDependencies({
        stylePackage: SAMPLE_STYLE_PACKAGE_FULL,
        convert: (request) => {
          requests.push(request);
          return Promise.resolve(docxResult());
        },
      }),
    );
    await settled();

    pick('render-comments-selector', 'ALL');
    await userEvent.click(checkbox('include-unreferenced-comments'));

    const roles = field<HTMLSelectElement>('#roles-selector')!;
    Array.from(roles.options).forEach((option) => (option.selected = option.value !== 'relates_to'));
    roles.dispatchEvent(new Event('change', { bubbles: true }));
    pick('roles-direction-selector', 'REVERSE');
    pick('language', 'it');

    await userEvent.click(field<HTMLButtonElement>('#export-docx')!);

    const sent = JSON.parse(requests[0]) as Record<string, unknown>;
    expect(sent.renderComments).toBe('ALL');
    expect(sent.includeUnreferencedComments).toBe(false);
    expect(sent.linkedWorkitemRoles).toEqual(['depends_on', 'verifies']);
    expect(sent.linkRoleDirection).toBe('REVERSE');
    expect(sent.language).toBe('it');
  });

  it('reloads every field when another style package is picked', async () => {
    const packages: Record<string, StylePackageSettings> = {
      Default: SAMPLE_STYLE_PACKAGE,
      Specification: { ...SAMPLE_STYLE_PACKAGE, paperSize: 'A3', preserveTableStyles: true },
    };
    open({
      ...sampleDependencies(),
      loadPackage: (_send, name) => Promise.resolve(packages[name] ?? SAMPLE_STYLE_PACKAGE),
    });
    await settled();
    expect(field('#paper-size-selector')).toBeNull();

    pick('style-package-select', 'Specification');

    await vi.waitFor(() => expect(selected('paper-size-selector')).toBe('A3'));
    expect(checkbox('preserve-table-styles').checked).toBe(true);
  });
});

describe('what the panel says when it cannot load', () => {
  it('reports a style package that cannot be read', async () => {
    open({ ...sampleDependencies(), loadPackage: () => Promise.reject(new Error('HTTP 500')) });

    await vi.waitFor(() => expect(text('#style-package-error')).toContain('error loading style package settings'));
  });

  it('reports data that cannot be read', async () => {
    open({ ...sampleDependencies(), loadData: () => Promise.reject(new Error('HTTP 500')) });

    await vi.waitFor(() => expect(text('#style-package-error')).toContain('error loading style package settings'));
  });
});

describe('exporting', () => {
  it('sends what the form says and downloads the result under the file name shown', async () => {
    const requests: string[] = [];
    const downloads: string[] = [];
    open(
      sampleDependencies({
        stylePackage: SAMPLE_STYLE_PACKAGE_FULL,
        convert: (request) => {
          requests.push(request);
          return Promise.resolve(docxResult());
        },
        download: (_blob, name) => downloads.push(name),
      }),
    );
    await settled();

    await userEvent.click(field<HTMLButtonElement>('#export-docx')!);

    expect(downloads).toEqual(['E-Library Cross Link Issue.docx']);
    const sent = JSON.parse(requests[0]) as Record<string, unknown>;
    expect(sent.projectId).toBe('elibrary');
    expect(sent.locationPath).toBe('Default Space/Cross Link Issue');
    expect(sent.paperSize).toBe('A4');
    expect(sent.chapters).toEqual(['1', '2']);
    expect(sent.fileName).toBe('E-Library Cross Link Issue.docx');
  });

  it('appends .docx to a name the user typed without it', async () => {
    const downloads: string[] = [];
    open(
      sampleDependencies({
        convert: () => Promise.resolve(docxResult()),
        download: (_blob, name) => downloads.push(name),
      }),
    );
    await settled();

    await userEvent.fill(field<HTMLInputElement>('#filename')!, 'My Export');
    await userEvent.click(field<HTMLButtonElement>('#export-docx')!);

    expect(downloads).toEqual(['My Export.docx']);
  });

  it('falls back to the default name when the user cleared the field', async () => {
    const downloads: string[] = [];
    open(
      sampleDependencies({
        convert: () => Promise.resolve(docxResult()),
        download: (_blob, name) => downloads.push(name),
      }),
    );
    await settled();

    await userEvent.clear(field<HTMLInputElement>('#filename')!);
    await userEvent.click(field<HTMLButtonElement>('#export-docx')!);

    expect(downloads).toEqual(['E-Library Cross Link Issue.docx']);
  });

  it('shows the warning a conversion came back with, as text rather than markup', async () => {
    open(sampleDependencies({ convert: () => Promise.resolve(docxResult('2 image(s) were not exported')) }));
    await settled();

    await userEvent.click(field<HTMLButtonElement>('#export-docx')!);

    expect(text('#export-warning')).toBe('2 image(s) were not exported');
  });

  it('shows why a conversion failed', async () => {
    open(sampleDependencies({ convert: () => Promise.reject(new Error('The document has no content')) }));
    await settled();

    await userEvent.click(field<HTMLButtonElement>('#export-docx')!);

    await vi.waitFor(() =>
      expect(text('#export-error')).toBe('Error occurred during DOCX generation:\nThe document has no content'),
    );
  });

  it('says only that it failed when the server gave no reason', async () => {
    open(sampleDependencies({ convert: () => Promise.reject(new Error('')) }));
    await settled();

    await userEvent.click(field<HTMLButtonElement>('#export-docx')!);

    await vi.waitFor(() => expect(text('#export-error')).toBe('Error occurred during DOCX generation'));
  });

  it('disables the panel and shows the spinner while an export runs', async () => {
    // The sample conversion never completes, which is the in-progress state
    open(sampleDependencies());
    await settled();

    await userEvent.click(field<HTMLButtonElement>('#export-docx')!);

    // The panel is one fieldset, so `:disabled` is what says a control is out of reach - an inherited
    // disabled state does not set the control's own `disabled` property.
    expect(field('#export-docx')!.matches(':disabled')).toBe(true);
    expect(field('#filename')!.matches(':disabled')).toBe(true);
    expect(field('#specific-chapters')!.matches(':disabled')).toBe(true);
    expect(getComputedStyle(field('#export-docx-progress')!).display).toBe('inline-block');
  });

  it('refuses to export on a bad chapters entry, and marks the field', async () => {
    open(sampleDependencies({ stylePackage: SAMPLE_STYLE_PACKAGE_FULL }));
    await settled();

    await userEvent.fill(field<HTMLInputElement>('#chapters')!, 'one, two');
    await userEvent.click(field<HTMLButtonElement>('#export-docx')!);

    expect(text('#export-error')).toContain('comma separated list of integer values');
    expect(field('#chapters')!.className).toContain('error');
    // Nothing was started, so the panel is still usable
    expect(field<HTMLButtonElement>('#export-docx')!.disabled).toBe(false);
  });

  it('disables the export, with the reason, for a user who may not export', async () => {
    open(sampleDependencies({ data: { exportPermission: 'denied' } }));
    await settled();

    const exportButton = field<HTMLButtonElement>('#export-docx')!;
    expect(exportButton.disabled).toBe(true);
    expect(exportButton.title).toBe('You are not allowed to export DOCX for this project');
  });

  it('disables the export when the permission could not be read, without claiming a refusal', async () => {
    // Fail closed, the way the DLE toolbar's button does - but the panel does not know the user is
    // unauthorized, so it must not say so.
    open(sampleDependencies({ data: { exportPermission: 'unknown' } }));
    await settled();

    const exportButton = field<HTMLButtonElement>('#export-docx')!;
    expect(exportButton.disabled).toBe(true);
    expect(exportButton.title).toBe('Could not check whether you are allowed to export. Please, reload the page.');
    expect(exportButton.title).not.toContain('not allowed');
  });
});
