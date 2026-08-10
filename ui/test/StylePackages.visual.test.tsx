import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from 'vitest-browser-react';
import { filled, snapshotFeature } from './visualHelpers';

// Docker-only snapshot of the Style Packages page: the two-column form with every switch on, which is
// what pins the alignment of the labels, the reserved space of a switched-off value, and the sections.

const origUrl = window.location.pathname + window.location.search;

const SCOPE = 'project/elibrary/';

const childNames = (name: string) => [
  { name: 'Default', scope: SCOPE },
  { name, scope: '' },
];

const STORED = {
  matchingQuery: 'type:testrun',
  weight: 42.5,
  exposeSettings: true,
  template: 'With logo',
  localization: 'German',
  orientation: 'LANDSCAPE',
  paperSize: 'A3',
  imageDensity: 'DPI_300',
  preserveTableStyles: true,
  webhooks: 'Rewriter',
  removalSelector: 'img.decoration',
  renderComments: 'ALL',
  includeUnreferencedComments: true,
  cutEmptyChapters: true,
  cutEmptyWorkitemAttributes: true,
  cutLocalURLs: true,
  specificChapters: '1,2',
  language: 'fr',
  linkedWorkitemRoles: ['relates_to'],
  linkRoleDirection: 'DIRECT',
};

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
  document.cookie = 'selected-configuration-style-package=; path=/; max-age=0';
});

describe.skipIf(!__PIXEL_REFERENCES__)('Style Packages page visual', () => {
  it('a package loaded, every setting overridden', async () => {
    await snapshotFeature(
      'style-package',
      [
        { method: 'GET', match: /\/webhooks\/status/, json: { enabled: true } },
        { method: 'GET', match: /\/link-role-names/, json: ['relates_to', 'verifies'] },
        { method: 'GET', match: /\/settings\/templates\/names\?/, json: childNames('With logo') },
        { method: 'GET', match: /\/settings\/localization\/names\?/, json: childNames('German') },
        { method: 'GET', match: /\/settings\/webhooks\/names\?/, json: childNames('Rewriter') },
        {
          method: 'GET',
          match: /\/settings\/style-package\/names\?/,
          json: [
            { name: 'Reports', scope: SCOPE },
            { name: 'Default', scope: SCOPE },
          ],
        },
        { method: 'GET', match: /\/settings\/style-package\/names\/[^/]+\/content/, json: STORED },
        { method: 'GET', match: /\/settings\/style-package\/names\/[^/]+\/revisions/, json: [] },
      ],
      filled('#matching-query'),
      'style-packages-loaded',
      SCOPE,
    );
    expect(true).toBe(true);
  });
});
