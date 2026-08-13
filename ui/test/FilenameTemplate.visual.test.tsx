import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from 'vitest-browser-react';
import { filled, snapshotFeature } from './visualHelpers';

// Docker-only snapshot of the filename template page: the editor with its Velocity highlighting, the
// Save / Cancel / Default / Revisions toolbar, and the Quick Help with the supported variables.

const origUrl = window.location.pathname + window.location.search;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

describe.skipIf(!__PIXEL_REFERENCES__)('Filename template page visual', () => {
  it('a stored template and the supported variables', async () => {
    await snapshotFeature(
      'filename',
      [
        {
          method: 'GET',
          match: /\/settings\/filename-template\/names\/Default\/content/,
          json: { documentNameTemplate: '{{ PROJECT_NAME }}-{{ DOCUMENT_ID }}-{{ REVISION }}' },
        },
        { method: 'GET', match: /\/settings\/filename-template\/default-content/, json: { documentNameTemplate: '' } },
        { method: 'GET', match: /\/settings\/filename-template\/names\/[^/]+\/revisions/, json: [] },
      ],
      filled('#document-name-template'),
      'filename-template-loaded',
    );
    expect(true).toBe(true);
  });
});
