import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import App from '../src/App';
import { installFetchMock } from './mockFetch';
import type { Route } from './mockFetch';

// The filename template page: one Velocity template, no named configurations, and - unlike the PDF
// exporter's four template pages - a Default button that loads the built-in template into the editor
// instead of a read-only tab. That difference is the page's own behaviour and is what is pinned here.

const origUrl = window.location.pathname + window.location.search;

const STORED = '{{ PROJECT_NAME }}-{{ DOCUMENT_ID }}';
const BUILT_IN = '$page.titleOrName';

const routes = (overrides: Route[] = []): Route[] => [
  ...overrides,
  {
    method: 'GET',
    match: /\/settings\/filename-template\/names\/Default\/content/,
    json: { documentNameTemplate: STORED },
  },
  { method: 'GET', match: /\/settings\/filename-template\/default-content/, json: { documentNameTemplate: BUILT_IN } },
  { method: 'PUT', match: /\/settings\/filename-template\/names\/Default\/content/, json: {} },
  { method: 'GET', match: /\/settings\/filename-template\/names\/[^/]+\/revisions/, json: [] },
];

const editor = () => document.querySelector<HTMLTextAreaElement>('#document-name-template')!;

const open = (list: Route[] = routes()) => {
  const fetchMock = installFetchMock(list);
  window.history.replaceState({}, '', '?feature=filename&embedded=true&scope=project/elibrary/');
  render(<App />);
  return fetchMock;
};

const clickButton = async (label: string) => {
  const button = Array.from(document.querySelectorAll<HTMLElement>('button, .sbb-btn')).find(
    (b) => b.textContent?.trim() === label,
  )!;
  await userEvent.click(button);
};

const answerDialog = async (label: string) => {
  await vi.waitFor(() => expect(document.querySelector('.rsp-modal')).not.toBeNull());
  Array.from(document.querySelectorAll<HTMLButtonElement>('.rsp-modal-footer .sbb-btn'))
    .find((b) => (b.textContent ?? '').trim() === label)!
    .click();
};

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

describe('Filename template page', () => {
  it('loads the stored template and the supported variables', async () => {
    open();

    await vi.waitFor(() => expect(editor().value).toBe(STORED));
    expect(document.body.textContent).toContain('DOCX Exporter: Filename template');
    expect(document.body.textContent).toContain('Supported special variables');
    // A single setting: no configuration selector.
    expect(document.querySelector('.configurations-pane')).toBeNull();
  });

  it('saves the template as the Default setting of this scope', async () => {
    const fetchMock = open();
    await vi.waitFor(() => expect(editor().value).toBe(STORED));

    await userEvent.fill(editor(), '{{ DOCUMENT_TITLE }}');
    await clickButton('Save');

    await vi.waitFor(() => {
      const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')!;
      expect(String(put[0])).toContain('/settings/filename-template/names/Default/content?scope=project%2Felibrary%2F');
      expect(JSON.parse(String(put[1]!.body))).toEqual({ documentNameTemplate: '{{ DOCUMENT_TITLE }}' });
    });
  });

  it('loads the built-in template into the editor without saving it', async () => {
    const fetchMock = open();
    await vi.waitFor(() => expect(editor().value).toBe(STORED));

    await clickButton('Default');
    await answerDialog('OK');

    await vi.waitFor(() => expect(editor().value).toBe(BUILT_IN));
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
  });

  it('reloads the stored template when the edit is cancelled', async () => {
    open();
    await vi.waitFor(() => expect(editor().value).toBe(STORED));
    await userEvent.fill(editor(), 'unsaved');

    await clickButton('Cancel');
    await answerDialog('OK');

    await vi.waitFor(() => expect(editor().value).toBe(STORED));
  });

  it('reports a setting it cannot read', async () => {
    open([
      {
        method: 'GET',
        match: /\/settings\/filename-template\/names\/Default\/content/,
        json: { message: 'nope' },
        status: 500,
      },
    ]);

    await vi.waitFor(() => expect(document.querySelector('.notifications .alert-error')).not.toBeNull());
  });

  it('reports a failing save', async () => {
    open(
      routes([
        {
          method: 'PUT',
          match: /\/settings\/filename-template\/names\/Default\/content/,
          json: { message: 'read-only scope' },
          status: 400,
        },
      ]),
    );
    await vi.waitFor(() => expect(editor().value).toBe(STORED));

    await clickButton('Save');

    await vi.waitFor(() => expect(document.body.textContent).toContain('read-only scope'));
  });
});
