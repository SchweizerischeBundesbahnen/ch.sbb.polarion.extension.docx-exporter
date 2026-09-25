import { pageViolations } from '@sbb-polarion/react-sbb-polarion/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { findFeature } from '../src/features';
import { dropdownsSettled } from './a11yHelpers';
import { installFetchMock, jsonResponse } from './mockFetch';

// The top-level feature router: `?feature=<id>` selects a page, anything unmatched (incl. bare `/`)
// renders the dev Landing stub. Also covers the findFeature lookup. The About page has a file of its own.

const origUrl = window.location.pathname + window.location.search;

const PROJECTS = { data: [{ id: 'elibrary', attributes: { name: 'E-Library' } }] };

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
  document.cookie = 'docx-exporter-dev-scope=; path=/; max-age=0';
});

describe('findFeature', () => {
  it('matches a known id and returns undefined otherwise', () => {
    // The ids are the contract with hivemodule.xml: an extender pointing at an id that is not here
    // opens a blank page in Polarion.
    expect(findFeature('about')?.id).toBe('about');
    expect(findFeature('disclaimer')?.id).toBe('disclaimer');
    expect(findFeature('user-guide')?.id).toBe('user-guide');
    expect(findFeature('filename')?.id).toBe('filename');
    expect(findFeature('style-package-weights')?.id).toBe('style-package-weights');
    expect(findFeature('authorization')?.id).toBe('authorization');
    expect(findFeature('nope')).toBeUndefined();
    expect(findFeature(null)).toBeUndefined();
  });
});

describe('App router', () => {
  it('renders the Landing stub with every feature linked when no feature is selected', async () => {
    installFetchMock([{ method: 'GET', match: /\/polarion\/rest\/v1\/projects/, json: PROJECTS }]);
    window.history.replaceState({}, '', '?'); // no feature -> Landing
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('.landing')).not.toBeNull());
    const links = Array.from(document.querySelectorAll<HTMLAnchorElement>('.feature-list a')).map((a) =>
      a.getAttribute('href'),
    );
    expect(links).toEqual([
      '?feature=about',
      '?feature=disclaimer',
      // The documentation site, in manifest reading order; reached from the documentation node and the
      // articles' cross-links, no admin menu entry of their own
      '?feature=quick-start',
      '?feature=user-guide',
      '?feature=configuration',
      '?feature=limitations',
      '?feature=upgrade',
      '?feature=filename',
      '?feature=style-package',
      '?feature=templates',
      '?feature=localization',
      '?feature=webhooks',
      '?feature=style-package-weights',
      '?feature=authorization',
      // Last, and the only entries no administration page points at: the development harnesses for the
      // document editor's export panel and for the dialog its toolbar button opens
      '?feature=side-panel',
      '?feature=export-popup',
    ]);
  });

  it('pre-selects the scope from the ?scope param so feature links carry it', async () => {
    installFetchMock([{ method: 'GET', match: /\/polarion\/rest\/v1\/projects/, json: PROJECTS }]);
    window.history.replaceState({}, '', '?scope=project/elibrary');
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('.landing')).not.toBeNull());
    await vi.waitFor(() => {
      const hrefs = Array.from(document.querySelectorAll<HTMLAnchorElement>('.feature-list a')).map((a) =>
        a.getAttribute('href'),
      );
      expect(hrefs.some((h) => h?.includes('scope=project%2Felibrary%2F'))).toBe(true);
    });
  });

  it('shows an error when projects cannot be loaded', async () => {
    installFetchMock([
      { method: 'GET', match: /\/polarion\/rest\/v1\/projects/, respond: () => jsonResponse({}, 401) },
    ]);
    window.history.replaceState({}, '', '?');
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('.landing .alert-error')).not.toBeNull());
    expect(document.querySelector('.alert-error')!.textContent).toContain('Could not load projects');
  });
});

describe('accessibility', () => {
  it('has no WCAG A/AA violations on the Landing stub', async () => {
    installFetchMock([{ method: 'GET', match: /\/polarion\/rest\/v1\/projects/, json: PROJECTS }]);
    window.history.replaceState({}, '', '?');
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('.landing')).not.toBeNull());
    await dropdownsSettled();
    expect(await pageViolations()).toEqual([]);
  });

  it('has no WCAG A/AA violations on the Landing stub with its error shown', async () => {
    installFetchMock([
      { method: 'GET', match: /\/polarion\/rest\/v1\/projects/, respond: () => jsonResponse({}, 401) },
    ]);
    window.history.replaceState({}, '', '?');
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('.landing .alert-error')).not.toBeNull());
    await dropdownsSettled();
    expect(await pageViolations()).toEqual([]);
  });
});

describe('documentation articles', () => {
  it('renders the generated article a DocArticle feature points at', async () => {
    installFetchMock([
      {
        method: 'GET',
        match: /configuration\.html$/,
        respond: () => new Response('<h1>Config</h1><p>Body</p>', { status: 200 }),
      },
    ]);
    window.history.replaceState({}, '', '?feature=configuration&embedded=true');
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('.markdown-body')).not.toBeNull());
    expect(document.body.textContent).toContain('Config');
  });
  // The link-resolution helper (docLinkTarget) is RSP-internal and unit-tested there (docsNav.test); the
  // interceptor behaviour reaches this app through DocLinkInterceptor, exercised via the render above.
});
