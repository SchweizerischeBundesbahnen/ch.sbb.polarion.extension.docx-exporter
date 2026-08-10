import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import App from '../src/App';
import { installFetchMock, jsonResponse } from './mockFetch';
import type { Route } from './mockFetch';

// The Templates page: the reference DOCX it attaches to a named configuration, what it displays about
// it, and what it stores. The page holds the document as bytes only - the assertions on the PUT body
// and on the download are what keep it that way.

const origUrl = window.location.pathname + window.location.search;

/** A stored document, as base64. The content is irrelevant: the server reads it, not the page. */
const STORED_BASE64 = 'UEsDBBQAAAAIAA==';

const DETAILS = { styleCount: 42, modifiedDate: '2024-06-13 08:45:12' };

const baseRoutes = (): Route[] => [
  {
    method: 'GET',
    match: /\/settings\/templates\/names\?/,
    json: [{ name: 'Default', scope: 'project/elibrary/' }],
  },
  { method: 'GET', match: /\/settings\/templates\/names\/[^/]+\/content/, json: { template: STORED_BASE64 } },
  { method: 'GET', match: /\/settings\/templates\/default-content/, json: { template: null } },
  { method: 'GET', match: /\/settings\/templates\/names\/[^/]+\/revisions/, json: [] },
  { method: 'PUT', match: /\/settings\/templates\/names\/[^/]+\/content/, json: {} },
  { method: 'POST', match: /\/template\/details/, json: DETAILS },
  { method: 'GET', match: /\/template$/, respond: () => new Response('built-in', { status: 200 }) },
];

/** The first matching route wins, so an override has to come before the defaults it replaces. */
const routesWith = (...overrides: Route[]): Route[] => [...overrides, ...baseRoutes()];

const open = (routes = baseRoutes()) => {
  const fetchMock = installFetchMock(routes);
  window.history.replaceState({}, '', '?feature=templates&embedded=true&scope=project/elibrary/');
  render(<App />);
  return fetchMock;
};

const panel = () => document.querySelector('.docx-panel')!;
const fileInfo = () => document.querySelector('.docx-panel .file-info')?.textContent ?? '';
const attached = () => document.querySelector('.docx-download') !== null;

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

/** Picks a file in the hidden file input the way the browser would, so React sees a `change`. */
const pickFile = (file: File) => {
  const input = document.querySelector<HTMLInputElement>('#template-file-upload')!;
  const transfer = new DataTransfer();
  transfer.items.add(file);
  input.files = transfer.files;
  input.dispatchEvent(new Event('change', { bubbles: true }));
};

const docxFile = (bytes: number[]) =>
  new File([new Uint8Array(bytes)], 'reference.docx', {
    type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  });

const loadedWithTemplate = async () => vi.waitFor(() => expect(attached()).toBe(true));

/** Collects the file names a download would have produced, instead of letting the browser fetch them. */
const captureDownloads = (): string[] => {
  const names: string[] = [];
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    names.push(this.download);
  });
  return names;
};

/** The revisions arrive after the table, so the row's button is what has to be waited for. */
const revertButton = async (): Promise<HTMLElement> => {
  await vi.waitFor(() => expect(document.querySelector('.revisions-table tbody button')).not.toBeNull());
  return document.querySelector<HTMLElement>('.revisions-table tbody button')!;
};

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.history.replaceState({}, '', origUrl);
  document.cookie = 'selected-configuration-templates=; path=/; max-age=0';
});

describe('Templates page', () => {
  it('shows what the server reads out of the stored template', async () => {
    open();
    await loadedWithTemplate();

    await vi.waitFor(() => expect(fileInfo()).toContain('Style Count: 42'));
    expect(fileInfo()).toContain('Last Modified Date: 2024-06-13 08:45:12');
  });

  it('sends the stored document to the details endpoint as bytes, not as base64', async () => {
    const fetchMock = open();
    await loadedWithTemplate();

    await vi.waitFor(() => {
      const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
      expect(post).toBeDefined();
      const body = post![1]!.body as Uint8Array;
      expect(body).toBeInstanceOf(Uint8Array);
      // The first two bytes of every DOCX, which base64 text would not begin with.
      expect(Array.from(body.subarray(0, 2))).toEqual([0x50, 0x4b]);
    });
  });

  it('offers the picker when the configuration has no template', async () => {
    open(
      routesWith({ method: 'GET', match: /\/settings\/templates\/names\/[^/]+\/content/, json: { template: null } }),
    );

    await vi.waitFor(() => expect(panel().textContent).toContain('No file provided'));
    expect(attached()).toBe(false);
  });

  it('says N/A when the document carries no modification date', async () => {
    open(routesWith({ method: 'POST', match: /\/template\/details/, json: { styleCount: 7 } }));
    await loadedWithTemplate();

    await vi.waitFor(() => expect(fileInfo()).toContain('Style Count: 7'));
    expect(fileInfo()).toContain('Last Modified Date: N/A');
  });

  it('attaches a chosen file and stores it base64 on save', async () => {
    const fetchMock = open(
      routesWith({ method: 'GET', match: /\/settings\/templates\/names\/[^/]+\/content/, json: { template: null } }),
    );
    await vi.waitFor(() => expect(panel().textContent).toContain('No file provided'));

    pickFile(docxFile([0x50, 0x4b, 0x03, 0x04]));
    await loadedWithTemplate();

    await clickButton('Save');
    await vi.waitFor(() => {
      const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT');
      expect(put).toBeDefined();
      expect(JSON.parse(String(put![1]!.body))).toEqual({ template: btoa('PK\x03\x04') });
    });
  });

  it('attaches nothing when the chosen file is not a valid docx', async () => {
    open(
      routesWith(
        { method: 'GET', match: /\/settings\/templates\/names\/[^/]+\/content/, json: { template: null } },
        { method: 'POST', match: /\/template\/details/, status: 400, json: {} },
      ),
    );
    await vi.waitFor(() => expect(panel().textContent).toContain('No file provided'));

    pickFile(docxFile([0x00, 0x01]));

    await vi.waitFor(() => expect(document.body.textContent).toContain('Uploaded file must be a valid docx file'));
    expect(attached()).toBe(false);
  });

  it('detaches the document without storing anything until save', async () => {
    const fetchMock = open();
    await loadedWithTemplate();

    await clickButton('Delete attached file');
    await vi.waitFor(() => expect(panel().textContent).toContain('No file provided'));
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);

    await clickButton('Save');
    await vi.waitFor(() => {
      const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT');
      expect(JSON.parse(String(put![1]!.body))).toEqual({ template: null });
    });
  });

  it('detaches the document when the defaults are loaded, the built-in default being no template', async () => {
    open();
    await loadedWithTemplate();

    await clickButton('Default');
    await answerDialog('OK');

    await vi.waitFor(() => expect(panel().textContent).toContain('No file provided'));
  });

  it('restores the stored document when the edit is cancelled', async () => {
    open();
    await loadedWithTemplate();

    await clickButton('Delete attached file');
    await vi.waitFor(() => expect(attached()).toBe(false));

    await clickButton('Cancel');
    await answerDialog('OK');

    await vi.waitFor(() => expect(attached()).toBe(true));
  });

  it('downloads the attached document under the configuration name', async () => {
    const downloads = captureDownloads();
    open();
    await loadedWithTemplate();

    await userEvent.click(document.querySelector<HTMLElement>('.docx-download')!);

    await vi.waitFor(() => expect(downloads).toEqual(['Template_Default.docx']));
  });

  it('downloads the attached document from the keyboard too, and only on Enter or Space', async () => {
    const downloads = captureDownloads();
    open();
    await loadedWithTemplate();

    const icon = document.querySelector<HTMLElement>('.docx-download')!;
    icon.focus();
    await userEvent.keyboard('{Tab}');
    icon.focus();
    await userEvent.keyboard('a');
    expect(downloads).toEqual([]);

    await userEvent.keyboard('{Enter}');
    await userEvent.keyboard(' ');

    await vi.waitFor(() => expect(downloads.length).toBe(2));
  });

  it('keeps the edit when the cancel confirmation is dismissed', async () => {
    open();
    await loadedWithTemplate();

    await clickButton('Delete attached file');
    await vi.waitFor(() => expect(attached()).toBe(false));

    await clickButton('Cancel');
    await answerDialog('Cancel');

    // Nothing reloaded, so the detached state survives the dismissed dialog.
    await vi.waitFor(() => expect(attached()).toBe(false));
  });

  it('keeps the edit when the default confirmation is dismissed', async () => {
    open();
    await loadedWithTemplate();

    await clickButton('Default');
    await answerDialog('Cancel');

    await vi.waitFor(() => expect(attached()).toBe(true));
  });

  it('reports a save that fails, with the message the server sent', async () => {
    open(
      routesWith({
        method: 'PUT',
        match: /\/settings\/templates\/names\/[^/]+\/content/,
        respond: () => jsonResponse({ message: 'read-only scope' }, 500),
      }),
    );
    await loadedWithTemplate();

    await clickButton('Save');

    await vi.waitFor(() => expect(document.body.textContent).toContain('read-only scope'));
  });

  it('reports a failure to load the configuration when the edit is cancelled', async () => {
    let failing = false;
    open(
      routesWith({
        method: 'GET',
        match: /\/settings\/templates\/names\/[^/]+\/content/,
        respond: () =>
          failing ? jsonResponse({ errorMessage: 'gone' }, 500) : jsonResponse({ template: STORED_BASE64 }, 200),
      }),
    );
    await loadedWithTemplate();

    failing = true;
    await clickButton('Cancel');
    await answerDialog('OK');

    await vi.waitFor(() => expect(document.querySelector('.alert-error')).not.toBeNull());
  });

  it('restores a revision into the form without storing it', async () => {
    const fetchMock = open(
      routesWith({
        method: 'GET',
        match: /\/settings\/templates\/names\/[^/]+\/revisions/,
        json: [{ name: '1234', date: '2024-06-13', baseline: null }],
      }),
    );
    await loadedWithTemplate();

    await clickButton('Revisions');
    await userEvent.click(await revertButton());

    await vi.waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => String(url).includes('revision=1234'))).toBe(true),
    );
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
  });

  it('reports a failure to download the built-in reference document', async () => {
    open(routesWith({ method: 'GET', match: /\/template$/, status: 500, json: {} }));
    await loadedWithTemplate();

    await clickButton('download');

    await vi.waitFor(() => expect(document.body.textContent).toContain('Error downloading the built-in template.'));
  });

  it('downloads the built-in reference document through the REST layer, not as a plain link', async () => {
    const fetchMock = open();
    await loadedWithTemplate();

    await clickButton('download');

    await vi.waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/template'))).toBe(true));
  });

  it('reports a failure to load the configuration', async () => {
    open(
      routesWith({
        method: 'GET',
        match: /\/settings\/templates\/names\/[^/]+\/content/,
        respond: () => jsonResponse({ errorMessage: 'boom' }, 500),
      }),
    );

    await vi.waitFor(() => expect(document.querySelector('.alert-error')).not.toBeNull());
  });
});
