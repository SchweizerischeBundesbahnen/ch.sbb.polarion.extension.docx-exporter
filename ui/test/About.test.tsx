import { pageViolations } from '@sbb-polarion/react-sbb-polarion/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { installFetchMock } from './mockFetch';

// The About page is a thin wrapper feeding react-sbb-polarion's shared About component this app's
// endpoints. Rendered through the router with the generic About endpoints mocked; RSP owns the deeper
// coverage of the component itself.

const origUrl = window.location.pathname + window.location.search;

const aboutRoutes = () => [
  {
    method: 'GET',
    match: /\/version$/,
    json: { bundleName: 'DOCX Exporter', bundleVendor: 'SBB', bundleVersion: '1.0.0' },
  },
  { method: 'GET', match: /\/configuration-properties$/, json: { properties: [], obsoleteProperties: [] } },
  { method: 'GET', match: /\/configuration-status/, json: [] },
  { method: 'GET', match: /\/readme$/, respond: () => new Response('<h1>Readme</h1>', { status: 200 }) },
];

const open = () => {
  installFetchMock(aboutRoutes());
  window.history.replaceState({}, '', '?feature=about&embedded=true');
  render(<App />);
};

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

describe('About page', () => {
  it('renders the shared About page with the extension info and icon', async () => {
    open();
    await vi.waitFor(() => expect(document.querySelector('.about-table')).not.toBeNull());
    expect(document.body.textContent).toContain('DOCX Exporter');
    expect(document.querySelector('.about-page .app-icon')).not.toBeNull();
  });
});

describe('accessibility', () => {
  it('has no WCAG A/AA violations', async () => {
    open();
    await vi.waitFor(() => expect(document.querySelector('.about-table')).not.toBeNull());
    await vi.waitFor(() => expect(document.body.textContent).toContain('Readme'));
    expect(await pageViolations()).toEqual([]);
  });
});
