import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import ToastHost from '../src/components/ToastHost';
import { EXPORT_ERROR, EXPORT_SUCCESS, reportFailure, reportSuccess } from '../src/export/reporting';
import type { ToastKind } from './toasts';
import { clearToasts } from './toasts';

// The toast host every surface shares (src/components/ToastHost.tsx): which of the mounted hosts reports,
// and what becomes of a report when that changes. Two hosts are on the editor page whenever a document is
// open - the Document Properties side panel and the "Export to DOCX" dialog over it - and `toast()` is one
// queue for both of them, so this is the whole of what keeps the two apart.
//
// Hosts rather than whole surfaces: a host is a host wherever it is mounted, and a bare one is the only way
// to hold two of them, to rank them and to take one away on its own.

const FAILED = `${EXPORT_ERROR}: The document has no content`;

const reportItFailed = () => reportFailure(EXPORT_ERROR, new Error('The document has no content'));

/** What a toast of this kind says inside this host, or '' where the host has none. */
const said = (host: HTMLElement, kind: ToastKind = 'success'): string =>
  host.querySelector(`[data-sonner-toast][data-type="${kind}"] [data-title]`)?.textContent ?? '';

const reads = (host: HTMLElement, kind: ToastKind, message: string) =>
  vi.waitFor(() => expect(said(host, kind)).toBe(message));

afterEach(async () => {
  await cleanup();
  clearToasts();
});

it('reports through the dialog while one is open, and through the panel again when it closes', async () => {
  const panel = await render(<ToastHost surface="panel" />);
  reportSuccess('The panel is reporting');
  await reads(panel.container, 'success', 'The panel is reporting');

  // The dialog, opened over the panel. RSP's Toaster is sonner's, which renders nothing at all until it has
  // a toast, so where a report lands is what says which host is the one reporting.
  const dialog = await render(<ToastHost surface="dialog" />);
  reportSuccess('The dialog is reporting');
  await reads(dialog.container, 'success', 'The dialog is reporting');
  expect(said(panel.container)).toBe('');
  expect(document.querySelectorAll('[data-sonner-toast]').length).toBe(1);

  await dialog.unmount();
  reportSuccess('The panel is reporting again');
  await reads(panel.container, 'success', 'The panel is reporting again');
});

it('leaves the dialog reporting when the panel mounts its host after it', async () => {
  // What mount order alone got wrong. The panel renders its host below its own loading state, so a dialog
  // opened while the properties pane still reads `Loading...` is the older host by the time those reads come
  // back - and every report of that export would go to the pane behind the dialog's backdrop.
  const dialog = await render(<ToastHost surface="dialog" />);
  reportItFailed();
  await reads(dialog.container, 'error', FAILED);

  const panel = await render(<ToastHost surface="panel" />);
  reportSuccess(EXPORT_SUCCESS);

  await reads(dialog.container, 'success', EXPORT_SUCCESS);
  expect(said(panel.container, 'success')).toBe('');
  // The panel took nothing over, so what the dialog is showing is untouched: the failure it reported before
  // the panel mounted is still there, waiting to be dismissed.
  expect(said(dialog.container, 'error')).toBe(FAILED);
});

it('lets the newest report where two hosts rank alike', async () => {
  // Two hosts of one surface, which is the development harness opening a second dialog over the first, and
  // the case mount order is still the answer to.
  const first = await render(<ToastHost surface="dialog" />);
  const second = await render(<ToastHost surface="dialog" />);
  reportSuccess('The newest is reporting');

  await reads(second.container, 'success', 'The newest is reporting');
  expect(said(first.container)).toBe('');
});

it('does not carry a report into the surface that takes over', async () => {
  const panel = await render(<ToastHost surface="panel" />);
  reportItFailed();
  await reads(panel.container, 'error', FAILED);

  // A failure waits to be dismissed, so it is still in the queue when the dialog opens over the panel.
  // Sonner would replay it into the dialog's own Toaster, which is what this host prevents.
  const dialog = await render(<ToastHost surface="dialog" />);
  reportSuccess(EXPORT_SUCCESS);

  // The dialog's own report is on screen, and a replayed one is published to its Toaster when that
  // subscribes - before this - so its absence here is the fix and not a race.
  await reads(dialog.container, 'success', EXPORT_SUCCESS);
  expect(said(dialog.container, 'error')).toBe('');
});

it('takes a report back with the surface that made it', async () => {
  const panel = await render(<ToastHost surface="panel" />);
  const dialog = await render(<ToastHost surface="dialog" />);
  reportItFailed();
  await reads(dialog.container, 'error', FAILED);

  // Closing the dialog. What it reported was reported to a user who is now looking at the properties pane,
  // where it would read as that pane's own.
  await dialog.unmount();
  reportSuccess('The panel is reporting');

  await reads(panel.container, 'success', 'The panel is reporting');
  expect(said(panel.container, 'error')).toBe('');
});

it('keeps a report raised while the first host is still mounting', async () => {
  // The single-host case, which is every administration page: App renders the host above the page, so a
  // page reporting from a mount effect reports into a queue no host is reading yet. Sonner's replay is what
  // puts that on screen, and there is no previous surface for it to have belonged to.
  reportSuccess('Data successfully saved.');
  const page = await render(<ToastHost />);

  await reads(page.container, 'success', 'Data successfully saved.');
});
