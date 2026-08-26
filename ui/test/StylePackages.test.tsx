import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import App from '../src/App';
import { installFetchMock, jsonResponse } from './mockFetch';
import type { FetchMock, Route } from './mockFetch';

// The Style Packages page: the three child settings a package points at, the switches it carries, and
// what each of them writes into the stored document. The switches that mean "not overridden" are the
// point of most of this file: they store null, not a stale value.

const origUrl = window.location.pathname + window.location.search;

const SCOPE = 'project/elibrary/';

/** Names of a child setting: one of this scope, one inherited from the global scope. */
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

const baseRoutes = (): Route[] => [
  { method: 'GET', match: /\/webhooks\/status/, json: { enabled: true } },
  { method: 'GET', match: /\/link-role-names/, json: ['relates_to', 'verifies'] },
  { method: 'GET', match: /\/settings\/templates\/names\?/, json: childNames('With logo') },
  { method: 'GET', match: /\/settings\/localization\/names\?/, json: childNames('German') },
  { method: 'GET', match: /\/settings\/webhooks\/names\?/, json: childNames('Rewriter') },
  {
    method: 'GET',
    match: /\/settings\/style-package\/names\?/,
    // Reports first, so the pane preselects a package that is not the Default one: the Default package
    // hides the matching query, and most of this file is about the rest of the form.
    json: [
      { name: 'Reports', scope: SCOPE },
      { name: 'Default', scope: SCOPE },
    ],
  },
  { method: 'GET', match: /\/settings\/style-package\/names\/[^/]+\/content/, json: STORED },
  { method: 'GET', match: /\/settings\/style-package\/default-content/, json: { weight: 0 } },
  { method: 'GET', match: /\/settings\/style-package\/names\/[^/]+\/revisions/, json: [] },
  { method: 'PUT', match: /\/settings\/style-package\/names\/[^/]+\/content/, json: {} },
];

/** The first matching route wins, so an override has to come before the defaults it replaces. */
const routesWith = (...overrides: Route[]): Route[] => [...overrides, ...baseRoutes()];

const open = (routes = baseRoutes()) => {
  const fetchMock = installFetchMock(routes);
  window.history.replaceState({}, '', `?feature=style-package&embedded=true&scope=${SCOPE}`);
  render(<App />);
  return fetchMock;
};

const input = (id: string) => document.querySelector<HTMLInputElement>(`#${id}`)!;
const select = (id: string) => document.querySelector<HTMLSelectElement>(`#${id}`);

/** The form is filled once the selected style package's content has landed. */
const loaded = async () => vi.waitFor(() => expect(input('style-package-weight').value).toBe('42.5'));

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

/** Picks a value on a SearchableSelect through the <select> it keeps as its source of truth. */
const choose = async (element: HTMLSelectElement, value: string) => {
  element.value = value;
  element.dispatchEvent(new Event('change', { bubbles: true }));
  await vi.waitFor(() => expect(element.value).toBe(value));
};

const pick = (id: string, value: string) => choose(select(id)!, value);

/** The configuration selector of the pane, which carries no id of its own. */
const selectPackage = (name: string) =>
  choose(document.querySelector<HTMLSelectElement>('.configurations-pane select')!, name);

/** The body of the last PUT, which is the stored document the page produced. */
const savedBody = async (fetchMock: FetchMock): Promise<Record<string, unknown>> => {
  let body: Record<string, unknown> | undefined;
  await vi.waitFor(() => {
    const put = fetchMock.mock.calls.filter(([, init]) => init?.method === 'PUT').pop();
    expect(put).toBeDefined();
    body = JSON.parse(String(put![1]!.body)) as Record<string, unknown>;
  });
  return body!;
};

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.history.replaceState({}, '', origUrl);
  document.cookie = 'selected-configuration-style-package=; path=/; max-age=0';
});

describe('Style Packages page', () => {
  it('loads a stored package into every control', async () => {
    open();
    await loaded();

    expect(input('matching-query').value).toBe('type:testrun');
    expect(input('exposeSettings').checked).toBe(true);
    expect(select('template-select')!.value).toBe('With logo');
    expect(select('localization-select')!.value).toBe('German');
    expect(input('orientation').checked).toBe(true);
    expect(select('orientation-select')!.value).toBe('LANDSCAPE');
    expect(input('paper-size').checked).toBe(true);
    expect(select('paper-size-select')!.value).toBe('A3');
    expect(input('image-density').checked).toBe(true);
    expect(select('image-density-select')!.value).toBe('DPI_300');
    expect(input('preserve-table-styles').checked).toBe(true);
    expect(input('webhooks-checkbox').checked).toBe(true);
    expect(input('render-comments').checked).toBe(true);
    expect(select('render-comments-select')!.value).toBe('ALL');
    expect(input('include-unreferenced-comments').checked).toBe(true);
    expect(input('cut-empty-chapters').checked).toBe(true);
    expect(input('cut-empty-wi-attributes').checked).toBe(true);
    expect(input('cut-urls').checked).toBe(true);
    expect(input('specific-chapters').checked).toBe(true);
    expect(input('chapters').value).toBe('1,2');
    expect(input('localization').checked).toBe(true);
    expect(select('language-select')!.value).toBe('fr');
    expect(input('selected-roles').checked).toBe(true);
    expect(select('link-role-direction-select')!.value).toBe('DIRECT');
    expect(input('removal-selector-input').value).toBe('img.decoration');
  });

  it('saves the document it loaded, field for field', async () => {
    const fetchMock = open();
    await loaded();

    await clickButton('Save');

    expect(await savedBody(fetchMock)).toEqual({ ...STORED, weight: 42.5 });
  });

  it('stores null for a page setting that is not overridden', async () => {
    const fetchMock = open();
    await loaded();

    await userEvent.click(input('orientation'));
    await userEvent.click(input('paper-size'));
    await userEvent.click(input('image-density'));
    await clickButton('Save');

    const body = await savedBody(fetchMock);
    expect(body.orientation).toBeNull();
    expect(body.paperSize).toBeNull();
    expect(body.imageDensity).toBeNull();
  });

  it('keeps the value behind a switch that is turned off and on again', async () => {
    const fetchMock = open();
    await loaded();

    await userEvent.click(input('paper-size'));
    await userEvent.click(input('paper-size'));
    await clickButton('Save');

    expect((await savedBody(fetchMock)).paperSize).toBe('A3');
  });

  it('stores null for the other switched-off settings, and drops the comment sub-option with them', async () => {
    const fetchMock = open();
    await loaded();

    await userEvent.click(input('webhooks-checkbox'));
    await userEvent.click(input('render-comments'));
    await userEvent.click(input('specific-chapters'));
    await userEvent.click(input('localization'));
    await userEvent.click(input('selected-roles'));
    await clickButton('Save');

    const body = await savedBody(fetchMock);
    expect(body.webhooks).toBeNull();
    expect(body.renderComments).toBeNull();
    expect(body.includeUnreferencedComments).toBe(false);
    expect(body.specificChapters).toBeNull();
    expect(body.language).toBeNull();
    expect(body.linkedWorkitemRoles).toBeNull();
    expect(body.linkRoleDirection).toBeNull();
  });

  it('hides the comment sub-option while comments are off', async () => {
    open();
    await loaded();

    await userEvent.click(input('render-comments'));

    await vi.waitFor(() => expect(document.querySelector('#include-unreferenced-comments')).toBeNull());
  });

  it('offers no matching query on the Default package, which applies to every document', async () => {
    open();
    await loaded();

    expect(input('matching-query')).toBeDefined();

    await selectPackage('Default');

    await vi.waitFor(() => expect(document.querySelector('#matching-query')).toBeNull());
  });

  it('normalizes the weight when the field is left', async () => {
    open();
    await loaded();

    await userEvent.fill(input('style-package-weight'), '250');
    input('style-package-weight').blur();

    await vi.waitFor(() => expect(input('style-package-weight').value).toBe('100'));

    // An emptied field is the "not a number" case: the legacy page fell back to 50 for it too.
    await userEvent.clear(input('style-package-weight'));
    input('style-package-weight').blur();

    await vi.waitFor(() => expect(input('style-package-weight').value).toBe('50'));
  });

  it('falls back to Default when the stored child configuration is gone from the scope', async () => {
    const fetchMock = open(
      routesWith({
        method: 'GET',
        match: /\/settings\/style-package\/names\/[^/]+\/content/,
        json: { ...STORED, template: 'Deleted one' },
      }),
    );
    await loaded();

    await vi.waitFor(() => expect(select('template-select')!.value).toBe('Default'));

    await clickButton('Save');
    expect((await savedBody(fetchMock)).template).toBe('Default');
  });

  it('marks a child configuration inherited from a parent scope', async () => {
    open();
    await loaded();

    const options = Array.from(select('template-select')!.options);
    // The name stays plain: the marker is the `parent` class, which the shared dropdown paints as a
    // small italic "global" on the right of the option.
    expect(options.map((o) => o.textContent)).toEqual(['Default', 'With logo']);
    expect(options.map((o) => o.className)).toEqual(['', 'parent']);
  });

  it('saves what the dropdowns were changed to', async () => {
    const fetchMock = open();
    await loaded();

    await pick('template-select', 'Default');
    await pick('render-comments-select', 'OPEN');
    await pick('language-select', 'it');
    await pick('link-role-direction-select', 'REVERSE');
    await clickButton('Save');

    const body = await savedBody(fetchMock);
    expect(body.template).toBe('Default');
    expect(body.renderComments).toBe('OPEN');
    expect(body.language).toBe('it');
    expect(body.linkRoleDirection).toBe('REVERSE');
  });

  it('hides the webhooks row when the installation has webhooks switched off', async () => {
    open(routesWith({ method: 'GET', match: /\/webhooks\/status/, json: { enabled: false } }));
    await loaded();

    expect(document.querySelector('#webhooks-checkbox')).toBeNull();
  });

  it('hides the webhooks row when the status cannot be read, without claiming anything', async () => {
    open(routesWith({ method: 'GET', match: /\/webhooks\/status/, status: 500, json: {} }));
    await loaded();

    expect(document.querySelector('#webhooks-checkbox')).toBeNull();
  });

  it('reports that the child configuration names could not be loaded', async () => {
    open(routesWith({ method: 'GET', match: /\/settings\/templates\/names\?/, status: 500, json: {} }));

    await vi.waitFor(() =>
      expect(document.body.textContent).toContain('error loading names of children configurations'),
    );
  });

  it('treats an empty child configuration list as a failure, a package having nothing to point at', async () => {
    open(routesWith({ method: 'GET', match: /\/settings\/localization\/names\?/, json: [] }));

    await vi.waitFor(() =>
      expect(document.body.textContent).toContain('error loading names of children configurations'),
    );
  });

  it('reports that the link roles could not be loaded', async () => {
    open(routesWith({ method: 'GET', match: /\/link-role-names/, status: 500, json: {} }));

    await vi.waitFor(() => expect(document.body.textContent).toContain('error loading link role names'));
  });

  it('reports a save that fails, with the message the server sent', async () => {
    open(
      routesWith({
        method: 'PUT',
        match: /\/settings\/style-package\/names\/[^/]+\/content/,
        respond: () => jsonResponse({ message: 'read-only scope' }, 500),
      }),
    );
    await loaded();

    await clickButton('Save');

    await vi.waitFor(() => expect(document.body.textContent).toContain('read-only scope'));
  });

  it('reloads the stored package when the edit is cancelled', async () => {
    open();
    await loaded();

    await userEvent.click(input('cut-urls'));
    expect(input('cut-urls').checked).toBe(false);

    await clickButton('Cancel');
    await answerDialog('OK');

    await vi.waitFor(() => expect(input('cut-urls').checked).toBe(true));
  });

  it('keeps the edit when the cancel confirmation is dismissed', async () => {
    open();
    await loaded();

    await userEvent.click(input('cut-urls'));
    await clickButton('Cancel');
    await answerDialog('Cancel');

    await vi.waitFor(() => expect(input('cut-urls').checked).toBe(false));
  });

  it('loads the defaults into the form without storing them', async () => {
    const fetchMock = open();
    await loaded();

    await clickButton('Default');
    await answerDialog('OK');

    await vi.waitFor(() => expect(input('style-package-weight').value).toBe('0'));
    expect(input('cut-urls').checked).toBe(false);
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
  });

  it('keeps the form when the default confirmation is dismissed', async () => {
    open();
    await loaded();

    await clickButton('Default');
    await answerDialog('Cancel');

    await vi.waitFor(() => expect(input('style-package-weight').value).toBe('42.5'));
  });

  it('reports a failure to load the defaults', async () => {
    open(routesWith({ method: 'GET', match: /\/settings\/style-package\/default-content/, status: 500, json: {} }));
    await loaded();

    await clickButton('Default');
    await answerDialog('OK');

    await vi.waitFor(() => expect(document.querySelector('.alert-error')).not.toBeNull());
  });

  it('restores a revision into the form without storing it', async () => {
    const fetchMock = open(
      routesWith({
        method: 'GET',
        match: /\/settings\/style-package\/names\/[^/]+\/revisions/,
        json: [{ name: '1234', date: '2024-06-13', baseline: null }],
      }),
    );
    await loaded();

    await clickButton('Revisions');
    // The revisions arrive after the table, so the row's button is what has to be waited for.
    await vi.waitFor(() => expect(document.querySelector('.revisions-table tbody button')).not.toBeNull());
    await userEvent.click(document.querySelector<HTMLElement>('.revisions-table tbody button')!);

    await vi.waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => String(url).includes('revision=1234'))).toBe(true),
    );
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
  });

  it('reports a failure to load the package when the edit is cancelled', async () => {
    let failing = false;
    open(
      routesWith({
        method: 'GET',
        match: /\/settings\/style-package\/names\/[^/]+\/content/,
        respond: () => (failing ? jsonResponse({ errorMessage: 'gone' }, 500) : jsonResponse(STORED, 200)),
      }),
    );
    await loaded();

    failing = true;
    await clickButton('Cancel');
    await answerDialog('OK');

    await vi.waitFor(() => expect(document.querySelector('.alert-error')).not.toBeNull());
  });
});
