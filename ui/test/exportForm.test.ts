import { describe, expect, it } from 'vitest';
import { childValue, resolveLanguage, toExportForm } from '../src/export/exportForm';
import { buildExportParams, toRequestBody } from '../src/export/exportParams';
import { parseChapters } from '../src/export/validation';
import type { StylePackageSettings } from '../src/services/stylePackage';
import { SAMPLE_DOCUMENT, SAMPLE_STYLE_PACKAGE_FULL } from './sidePanelSamples';

// Reading a style package into the form and building the export request out of it: the two halves of the
// legacy ExportPanel.js that had nothing to do with the DOM. The DOCX-specific part is the page setup -
// orientation, paper size and image density are each a switch plus a value, and an unticked switch means
// "take what the reference template says" rather than "send the default".

const EMPTY: StylePackageSettings = {};

describe('reading a style package into the form', () => {
  it('falls back where the package overrides nothing', () => {
    const form = toExportForm(EMPTY);

    expect(form.template).toBe('Default');
    expect(form.localization).toBe('Default');
    expect(form.orientationEnabled).toBe(false);
    expect(form.orientation).toBe('PORTRAIT');
    expect(form.paperSizeEnabled).toBe(false);
    expect(form.paperSize).toBe('A4');
    expect(form.imageDensityEnabled).toBe(false);
    expect(form.imageDensity).toBe('DPI_96');
    expect(form.renderCommentsEnabled).toBe(false);
    expect(form.renderComments).toBe('OPEN');
    expect(form.linkRoleDirection).toBe('BOTH');
    expect(form.removalSelector).toBe('');
  });

  it('switches on every setting the package states, keeping its value', () => {
    const form = toExportForm(SAMPLE_STYLE_PACKAGE_FULL);

    expect(form.orientationEnabled).toBe(true);
    expect(form.paperSizeEnabled).toBe(true);
    expect(form.imageDensityEnabled).toBe(true);
    expect(form.preserveTableStyles).toBe(true);
    expect(form.renderCommentsEnabled).toBe(true);
    expect(form.specificChaptersEnabled).toBe(true);
    expect(form.specificChapters).toBe('1,2');
    expect(form.localizeEnums).toBe(true);
    expect(form.rolesEnabled).toBe(true);
    expect(form.linkedWorkitemRoles).toEqual(['relates_to']);
    expect(form.removalSelector).toBe('img.decorative');
  });

  it('lets the document language win where the package exposes its settings', () => {
    const exposed = toExportForm({ exposeSettings: true, language: 'it' }, { documentLanguage: 'de' });
    expect(exposed.language).toBe('de');

    const hidden = toExportForm({ exposeSettings: false, language: 'it' }, { documentLanguage: 'de' });
    expect(hidden.language).toBe('it');
  });

  it('matches a docLanguage by id or by display name, and ignores one it does not offer', () => {
    expect(resolveLanguage('DE')).toBe('de');
    expect(resolveLanguage('Italiano')).toBe('it');
    expect(resolveLanguage('en')).toBeUndefined();
    expect(resolveLanguage(null)).toBeUndefined();
  });
});

describe('what a child dropdown points at', () => {
  const options = [
    { id: 'Default', name: 'Default' },
    { id: 'SBB', name: 'SBB' },
  ];

  it('keeps a name the scope still offers', () => {
    expect(childValue(options, 'SBB')).toBe('SBB');
  });

  it('falls back to Default for a name the scope dropped', () => {
    expect(childValue(options, 'Gone')).toBe('Default');
  });

  it('leaves the stored reference alone while the option list is still empty', () => {
    expect(childValue([], 'SBB')).toBe('SBB');
  });
});

describe('the chapters entry', () => {
  it('accepts a comma separated list of positive integers, spaces and all', () => {
    expect(parseChapters('1, 2,4')).toEqual(['1', '2', '4']);
  });

  it('refuses anything else', () => {
    expect(parseChapters('one')).toBeUndefined();
    expect(parseChapters('0')).toBeUndefined();
    expect(parseChapters('01')).toBeUndefined();
    expect(parseChapters('')).toBeUndefined();
  });
});

describe('building the export request', () => {
  const built = (settings: StylePackageSettings) => buildExportParams(toExportForm(settings), SAMPLE_DOCUMENT);

  it('leaves the page setup out entirely where its switches are off', () => {
    const result = built(EMPTY);

    expect('error' in result).toBe(false);
    if ('error' in result) return;
    expect(result.params.orientation).toBeNull();
    expect(result.params.paperSize).toBeNull();
    expect(result.params.imageDensity).toBeNull();
    // ... and the null filter takes them out of the body, which is what the server reads as "not set"
    const body = JSON.parse(toRequestBody(result.params)) as Record<string, unknown>;
    expect('orientation' in body).toBe(false);
    expect('paperSize' in body).toBe(false);
    expect('imageDensity' in body).toBe(false);
  });

  it('carries the page setup where the switches are on', () => {
    const result = built(SAMPLE_STYLE_PACKAGE_FULL);

    if ('error' in result) throw new Error(result.error.message);
    expect(result.params.orientation).toBe('PORTRAIT');
    expect(result.params.paperSize).toBe('A4');
    expect(result.params.imageDensity).toBe('DPI_96');
    expect(result.params.preserveTableStyles).toBe(true);
    expect(result.params.chapters).toEqual(['1', '2']);
    expect(result.params.removalSelector).toBe('img.decorative');
  });

  it('sends no link role direction where no role is asked for', () => {
    const result = built(EMPTY);

    if ('error' in result) throw new Error(result.error.message);
    expect(result.params.linkedWorkitemRoles).toEqual([]);
    expect(result.params.linkRoleDirection).toBeNull();
  });

  it('drops the unreferenced comments switch when the comments are not rendered at all', () => {
    const form = toExportForm({ includeUnreferencedComments: true });
    const result = buildExportParams(form, SAMPLE_DOCUMENT);

    if ('error' in result) throw new Error(result.error.message);
    expect(result.params.renderComments).toBeNull();
    expect(result.params.includeUnreferencedComments).toBe(false);
  });

  it('reports the bad chapters entry instead of building a request', () => {
    const form = { ...toExportForm(EMPTY), specificChaptersEnabled: true, specificChapters: 'one' };
    const result = buildExportParams(form, SAMPLE_DOCUMENT);

    expect('error' in result).toBe(true);
    if (!('error' in result)) return;
    expect(result.error.field).toBe('chapters');
    expect(result.error.message).toContain('comma separated list of integer values');
  });

  it('says nothing about a document type, there being no such field', () => {
    const result = built(EMPTY);

    if ('error' in result) throw new Error(result.error.message);
    expect(JSON.parse(toRequestBody(result.params))).not.toHaveProperty('documentType');
  });
});
