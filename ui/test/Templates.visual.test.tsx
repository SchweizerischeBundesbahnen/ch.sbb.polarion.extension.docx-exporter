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

  it('a configuration with a template, reported by its size', async () => {
    await snapshotFeature(
      'templates',
      routes('UEsDBBQAAAAIAA=='),
      found('.docx-panel .file-info'),
      'templates-attached',
    );
    expect(true).toBe(true);
  });
});
