import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from 'vitest-browser-react';
import { found, snapshotFeature } from './visualHelpers';

// Docker-only snapshots of the Templates page: the two states of the panel, which are the page.

const origUrl = window.location.pathname + window.location.search;

const routes = (template: string | null) => [
  {
    method: 'GET',
    match: /\/settings\/templates\/names\?/,
    json: [{ name: 'Default', scope: 'project/elibrary/' }],
  },
  { method: 'GET', match: /\/settings\/templates\/names\/[^/]+\/content/, json: { template } },
  { method: 'GET', match: /\/settings\/templates\/default-content/, json: { template: null } },
  { method: 'GET', match: /\/settings\/templates\/names\/[^/]+\/revisions/, json: [] },
  {
    method: 'POST',
    match: /\/template\/details/,
    json: { styleCount: 42, modifiedDate: '2024-06-13 08:45:12' },
  },
];

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
  document.cookie = 'selected-configuration-templates=; path=/; max-age=0';
});

describe.skipIf(!__PIXEL_REFERENCES__)('Templates page visual', () => {
  it('a configuration with no template, offering the picker', async () => {
    await snapshotFeature('templates', routes(null), found('#template-file-upload'), 'templates-empty');
    expect(true).toBe(true);
  });

  it('a configuration with a template, described by the server', async () => {
    await snapshotFeature(
      'templates',
      routes('UEsDBBQAAAAIAA=='),
      // The details arrive after the panel, so the snapshot waits for the text, not for the panel.
      () => (document.querySelector('.docx-panel .file-info')?.textContent ?? '').includes('42'),
      'templates-attached',
    );
    expect(true).toBe(true);
  });
});
