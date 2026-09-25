import { pageViolations } from '@sbb-polarion/react-sbb-polarion/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import App from '../src/App';
import { DOC_ORDER } from '../src/docs/manifest';
import searchIndex from '../src/docs/search-index.json';
import { installFetchMock } from './mockFetch';

// The pages this app wires from shared components: the documentation-site articles (RSP `DocPage` over the
// `html/<id>.html` files the Maven build renders) and Authorization (RSP `AuthorizationSettings` over this
// extension's `authorization` setting). What is worth asserting here is the wiring - which endpoints are
// called and with which setting name - since the components themselves are covered in the library.

const origUrl = window.location.pathname + window.location.search;

afterEach(() => {
  cleanup();
  // Every dropdown keeps its popup in a portal on the body, which React unmounting does not take with it.
  document.querySelectorAll('.sd-portal').forEach((portal) => portal.remove());
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

/** The dropdown opens, closes and picks on mousedown, so the helpers below drive that event. */
const mousedown = (node: Element) =>
  node.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));

/** Each role set is a multi-select SearchableSelect, which inserts itself right after the <select> the
 *  component ids. Addressing it from that id keeps these helpers off the page order. */
const trigger = (kind: 'global' | 'project'): HTMLElement => {
  const container = document.querySelector(`#${kind}-roles`)?.nextElementSibling;
  if (!(container instanceof HTMLElement)) {
    throw new Error(`no ${kind} roles control`);
  }
  return container.querySelector<HTMLElement>('.sd-trigger-multi')!;
};

/** The roles granted in one control, as the chips painted on its trigger. */
const granted = (kind: 'global' | 'project'): string[] =>
  Array.from(trigger(kind).querySelectorAll('.sd-chip-label')).map((chip) => (chip.textContent ?? '').trim());

/** Ticks (or unticks) one role and waits for its chip to follow, which is what proves React took the
 *  change - so a Save right after reads the new selection rather than the previous render's. */
async function toggleRole(kind: 'global' | 'project', role: string) {
  const wasGranted = granted(kind).includes(role);
  mousedown(trigger(kind));
  const listbox = document.getElementById(trigger(kind).getAttribute('aria-controls')!)!;
  const option = Array.from(listbox.querySelectorAll('.option')).find((o) => (o.textContent ?? '').trim() === role);
  if (!option) {
    throw new Error(`no option for role "${role}"`);
  }
  mousedown(option);
  mousedown(trigger(kind));
  await vi.waitFor(() => expect(granted(kind).includes(role)).toBe(!wasGranted));
}

const authorizationRoutes = (globalRoles: string[], projectRoles: string[], selected: string[] = []) => [
  { method: 'GET', match: /\/roles\?/, json: { globalRoles, projectRoles } },
  {
    method: 'GET',
    match: /\/settings\/authorization\/names\/Default\/content/,
    json: { globalRoles: selected, projectRoles: [] },
  },
  { method: 'PUT', match: /\/settings\/authorization\/names\/Default\/content/, json: {} },
];

describe('User Guide page', () => {
  it('renders the generated article and shows the documentation-site frame', async () => {
    // User Guide is now part of the documentation site: DocArticle fetches the static user-guide.html
    // served next to the app bundle, wrapped in DocLayout (sidebar + on-this-page + prev/next).
    const fetchMock = installFetchMock([
      {
        method: 'GET',
        match: /\/html\/user-guide\.html$/,
        respond: () => new Response('<h1>User Guide</h1><h2 id="how-to">How to</h2><p>Steps.</p>'),
      },
    ]);
    window.history.replaceState({}, '', '?feature=user-guide&embedded=true');
    render(<App />);

    await vi.waitFor(() => expect(document.querySelector('article.markdown-body')).not.toBeNull());
    expect(document.body.textContent).toContain('Steps.');
    // the docs-site chrome is present and this article is the active sidebar entry
    expect(document.querySelector('.docs-nav-link-active')?.textContent).toBe('User Guide');
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/\/html\/user-guide\.html$/);
  });
});

describe('Authorization page', () => {
  it('lists the roles of the scope with the stored ones granted', async () => {
    installFetchMock(authorizationRoutes(['admin', 'developer'], ['project_admin'], ['admin']));
    window.history.replaceState({}, '', '?feature=authorization&embedded=true&scope=project/elibrary/');
    render(<App />);

    // Both controls, not just the first: they are upgraded asynchronously.
    await vi.waitFor(() => expect(document.querySelectorAll('.roles-group .sd-trigger-multi')).toHaveLength(2));
    expect(granted('global')).toEqual(['admin']);
    expect(granted('project')).toEqual([]);
    expect(document.body.textContent).toContain('DOCX Exporter: Authorization');
    // Global and project roles are two groups; the project one only appears when the scope has roles.
    expect(document.querySelectorAll('.roles-group').length).toBe(2);
  });

  it('saves the selection to this extension’s authorization setting', async () => {
    const fetchMock = installFetchMock(authorizationRoutes(['admin', 'developer'], []));
    window.history.replaceState({}, '', '?feature=authorization&embedded=true&scope=');
    render(<App />);
    // One group only: with no project roles in the scope that half of the page is not rendered.
    await vi.waitFor(() => expect(document.querySelectorAll('.roles-group .sd-trigger-multi')).toHaveLength(1));

    await toggleRole('global', 'developer');
    const save = Array.from(document.querySelectorAll<HTMLElement>('button, .sbb-btn')).find(
      (b) => b.textContent?.trim() === 'Save',
    )!;
    await userEvent.click(save);

    await vi.waitFor(() => {
      const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT');
      expect(put).toBeDefined();
      expect(String(put![0])).toContain('/settings/authorization/names/Default/content');
      expect(String(put![1]!.body)).toContain('developer');
    });
  });

  it('reports a scope whose roles cannot be read', async () => {
    installFetchMock([{ method: 'GET', match: /\/roles\?/, json: { message: 'no such scope' }, status: 400 }]);
    window.history.replaceState({}, '', '?feature=authorization&embedded=true');
    render(<App />);

    await vi.waitFor(() => expect(document.querySelector('.alert-error, .alert')).not.toBeNull());
  });
});

describe('accessibility', () => {
  const openArticle = (id: string, respond: () => Response) => {
    installFetchMock([{ method: 'GET', match: new RegExp(`/html/${id}\\.html$`), respond }]);
    window.history.replaceState({}, '', `?feature=${id}&embedded=true`);
    render(<App />);
  };

  const ARTICLE =
    '<h1>Article</h1><p><a href="#setup">Setup</a></p><h2 id="setup">Setup</h2>' +
    '<p>See <a href="configuration.html">Configuration</a>.</p>' +
    '<table><thead><tr><th>Key</th><th>Value</th></tr></thead><tbody><tr><td>a</td><td>b</td></tr></tbody></table>' +
    '<pre><code>mvn install</code></pre>';

  it.each(DOC_ORDER.map((doc) => doc.id))('has no WCAG A/AA violations on the %s article', async (id) => {
    openArticle(id, () => new Response(ARTICLE, { status: 200 }));
    await vi.waitFor(() => expect(document.querySelector('article.markdown-body h2')).not.toBeNull());
    expect(await pageViolations()).toEqual([]);
  });

  // The index is built from the articles Maven renders, so without them there is no search box to scan.
  it.skipIf(searchIndex.length === 0)('has no WCAG A/AA violations with search results open', async () => {
    openArticle('user-guide', () => new Response(ARTICLE, { status: 200 }));
    await vi.waitFor(() => expect(document.querySelector('article.markdown-body h2')).not.toBeNull());
    await userEvent.fill(document.querySelector<HTMLInputElement>('.docs-search-input')!, 'Pandoc');
    await vi.waitFor(() => expect(document.querySelector('.docs-search-result')).not.toBeNull());
    expect(await pageViolations()).toEqual([]);
  });

  // A failed request shows the same fallback, so one case covers both.
  it('has no WCAG A/AA violations on an article that was not generated', async () => {
    openArticle('user-guide', () => new Response('', { status: 200 }));
    await vi.waitFor(() => expect(document.body.textContent).toContain('This article has not been generated'));
    expect(await pageViolations()).toEqual([]);
  });

  it('has no WCAG A/AA violations on the Authorization page', async () => {
    installFetchMock(authorizationRoutes(['admin', 'developer'], ['project_admin'], ['admin']));
    window.history.replaceState({}, '', '?feature=authorization&embedded=true&scope=project/elibrary/');
    render(<App />);
    await vi.waitFor(() => expect(document.querySelectorAll('.roles-group .sd-trigger-multi')).toHaveLength(2));
    expect(await pageViolations()).toEqual([]);
  });

  it('has no WCAG A/AA violations on the Authorization page with its error shown', async () => {
    installFetchMock([{ method: 'GET', match: /\/roles\?/, json: { message: 'no such scope' }, status: 400 }]);
    window.history.replaceState({}, '', '?feature=authorization&embedded=true');
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('.alert-error, .alert')).not.toBeNull());
    expect(await pageViolations()).toEqual([]);
  });
});
