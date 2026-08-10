import type { SendRequest } from '@grigoriev/react-sbb-polarion';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadDocumentLanguage, loadPanelData, loadStylePackage } from '../src/export/exportData';
import { installFetchMock } from './mockFetch';
import type { Route } from './mockFetch';
import { SAMPLE_DOCUMENT } from './sidePanelSamples';

// What the server-rendered panel used to have substituted into its markup, now read over REST. These are
// the endpoints the DLE toolbar popup has always used, so what is asserted here is that the panel asks
// them the same questions - and what it does when one of them will not answer.

// The loader takes its transport as a parameter, so this stands in for the panel's `useRemote()` - which
// is a hook and cannot be called outside a component. What that hook itself does is covered by
// useRemote.test.tsx.
const sendRequest: SendRequest = ({ method, url, body, contentType }) =>
  fetch(`/polarion/docx-exporter/rest/internal${url}`, {
    method,
    headers: contentType ? { 'Content-Type': contentType } : {},
    body,
  });

const names = (...values: string[]) => values.map((name) => ({ name, scope: 'project/elibrary/' }));

const baseRoutes = (): Route[] => [
  { method: 'POST', match: /\/settings\/style-package\/suitable-names/, json: names('Specification', 'Default') },
  { method: 'GET', match: /\/settings\/templates\/names/, json: names('Default', 'SBB') },
  { method: 'GET', match: /\/settings\/localization\/names/, json: names('Default') },
  { method: 'GET', match: /\/settings\/webhooks\/names/, json: names('Default') },
  { method: 'GET', match: /\/link-role-names/, json: ['relates_to', 'verifies'] },
  { method: 'POST', match: /\/export-filename/, respond: () => new Response('E-Library Doc.docx') },
  { method: 'GET', match: /\/document-language/, respond: () => new Response('de') },
  { method: 'GET', match: /\/webhooks\/status/, json: { enabled: true } },
  { method: 'GET', match: /\/permissions\/export/, json: { permitted: true } },
];

const routesWith = (...overrides: Route[]): Route[] => [...overrides, ...baseRoutes()];

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('loading the panel data', () => {
  it('reads the style packages, the option lists and the rest of what the panel offers', async () => {
    installFetchMock(baseRoutes());

    const data = await loadPanelData(sendRequest, SAMPLE_DOCUMENT);

    // Weight order is the server's; the panel preselects the head of it, as the server-rendered panel did
    expect(data.stylePackages.map((option) => option.id)).toEqual(['Specification', 'Default']);
    expect(data.childNames.templates.map((option) => option.id)).toEqual(['Default', 'SBB']);
    expect(data.roles.map((option) => option.id)).toEqual(['relates_to', 'verifies']);
    expect(data.fileName).toBe('E-Library Doc.docx');
    expect(data.documentLanguage).toBe('de');
    expect(data.webhooksEnabled).toBe(true);
    expect(data.exportPermission).toBe('granted');
  });

  it('asks for the style packages of this document, the way the endpoint wants it', async () => {
    const fetchMock = installFetchMock(baseRoutes());

    await loadPanelData(sendRequest, SAMPLE_DOCUMENT);

    const call = fetchMock.mock.calls.find(([url]) => String(url).includes('suitable-names'))!;
    expect(JSON.parse(String(call[1]!.body))).toEqual([
      { projectId: 'elibrary', spaceId: 'Default Space', documentName: 'Cross Link Issue' },
    ]);
  });

  it('names the document in the file name request without saying what type it is', async () => {
    // ExportParams.java has no `documentType`, and the panel only ever exports a Live Document.
    const fetchMock = installFetchMock(baseRoutes());

    await loadPanelData(sendRequest, SAMPLE_DOCUMENT);

    const call = fetchMock.mock.calls.find(([url]) => String(url).includes('export-filename'))!;
    const sent = JSON.parse(String(call[1]!.body)) as Record<string, unknown>;
    expect(sent.projectId).toBe('elibrary');
    expect(sent.locationPath).toBe('Default Space/Cross Link Issue');
    expect('documentType' in sent).toBe(false);
  });

  it('fails when the style packages cannot be read - there is nothing to choose from', async () => {
    installFetchMock(routesWith({ method: 'POST', match: /suitable-names/, status: 500, json: {} }));

    await expect(loadPanelData(sendRequest, SAMPLE_DOCUMENT)).rejects.toThrow('HTTP 500');
  });

  it('fails when a child setting cannot be read', async () => {
    installFetchMock(routesWith({ method: 'GET', match: /\/settings\/templates\/names/, status: 500, json: {} }));

    await expect(loadPanelData(sendRequest, SAMPLE_DOCUMENT)).rejects.toThrow('HTTP 500');
  });

  it('shows the panel anyway when only a detail could not be read', async () => {
    // A file name the user can type themselves is no reason to withhold the panel.
    installFetchMock(
      routesWith(
        { method: 'GET', match: /\/link-role-names/, status: 500, json: {} },
        { method: 'POST', match: /\/export-filename/, status: 500, json: {} },
        { method: 'GET', match: /\/document-language/, status: 500, json: {} },
        { method: 'GET', match: /\/webhooks\/status/, status: 500, json: {} },
      ),
    );

    const data = await loadPanelData(sendRequest, SAMPLE_DOCUMENT);

    expect(data.roles).toEqual([]);
    expect(data.fileName).toBe('');
    expect(data.documentLanguage).toBeNull();
    expect(data.webhooksEnabled).toBe(false);
  });
});

describe('the export permission', () => {
  const permissionOf = async (route: Route) => {
    installFetchMock(routesWith(route));
    return (await loadPanelData(sendRequest, SAMPLE_DOCUMENT)).exportPermission;
  };

  it('is refused on anything but an explicit grant', async () => {
    await expect(
      permissionOf({ method: 'GET', match: /\/permissions\/export/, json: { permitted: false } }),
    ).resolves.toBe('denied');
    await expect(permissionOf({ method: 'GET', match: /\/permissions\/export/, json: {} })).resolves.toBe('denied');
  });

  it('is unknown rather than refused when the answer could not be read', async () => {
    // Both keep the button off; only a refusal is something the panel can give a reason for.
    await expect(permissionOf({ method: 'GET', match: /\/permissions\/export/, status: 500, json: {} })).resolves.toBe(
      'unknown',
    );
  });
});

describe('the single reads', () => {
  it('escapes the style package name and the scope into the content URL', async () => {
    const fetchMock = installFetchMock([{ method: 'GET', match: /\/settings\/style-package\/names/, json: {} }]);

    await loadStylePackage(sendRequest, 'SBB Spec', 'project/e library/');

    expect(String(fetchMock.mock.calls[0][0])).toContain('names/SBB%20Spec/content?scope=project%2Fe%20library%2F');
  });

  it('reads the document language as null when the custom field is unset', async () => {
    installFetchMock([{ method: 'GET', match: /\/document-language/, respond: () => new Response('') }]);

    await expect(loadDocumentLanguage(sendRequest, SAMPLE_DOCUMENT)).resolves.toBeNull();
  });

  it('asks for the document language at the revision the page is showing', async () => {
    const fetchMock = installFetchMock([
      { method: 'GET', match: /\/document-language/, respond: () => new Response('de') },
    ]);

    await loadDocumentLanguage(sendRequest, { ...SAMPLE_DOCUMENT, revision: '42' });

    expect(String(fetchMock.mock.calls[0][0])).toContain('revision=42');
  });
});
