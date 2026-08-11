import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import type { DocumentIdentity } from '../services/exportContext';
import { currentDocumentLocation, toDocumentIdentity } from '../services/exportContext';
import { mountInShadow } from '../services/shadowMount';
import ExportPopupModal from './ExportPopupModal';
import type { ExportPopupDependencies } from './ExportPopupModal';
import popupStyle from './export-popup.css?inline';

/**
 * Entry point for the "Export to DOCX" dialog, built by Vite into a fixed-name module
 * (`assets/export-popup.js`; the Vite input key `export-popup` sets the output name).
 *
 * `webapp/docx-exporter/js/starter.js` - the document editor toolbar button - imports this module and calls
 * {@link openExportPopup} on click. It is the only caller: every DOCX export is a Live Document, so there is
 * no report toolbar and no bulk export widget to open it too.
 *
 * The dialog is mounted inside a **shadow root** on a throwaway host appended to the page body, so its
 * styles are fully encapsulated on the editor page: RSP's stylesheet and `export-popup.css` are injected
 * into that root, and the SearchableDropdown popup portals into the same root. Closing unmounts React and
 * removes the host. That is what replaced the page-level micromodal library and the generic control
 * stylesheets the legacy popup needed injected before it could be opened.
 */
export interface OpenExportPopupOptions {
  /** Where the document is. Read from the page URL when not given, which is what happens in Polarion. */
  location?: DocumentIdentity;
  /**
   * What the dialog reaches outside itself for. Nothing in Polarion passes this - the toolbar button wants
   * the real endpoints - but the visual references need a dialog that reads no network, the way
   * `mountSidePanel` takes the panel's dependencies for the same reason.
   */
  deps?: ExportPopupDependencies;
}

/**
 * Closes whatever dialog a previous {@link openExportPopup} call left open, if any. Without this, a second
 * click on the toolbar button while the first dialog is still open would mount a second, independently
 * submittable dialog on top of it - the button carries no disabled state of its own while a dialog is open.
 *
 * This is what replaced the legacy popup's element reuse. It kept one `<div>` per popup id on the page and
 * refilled it, because the shared micromodal cached a Modal per id bound to the element it first saw; a
 * rebuilt element left that cache pointing at a detached node and later opens silently did nothing. There is
 * no such cache here - each open builds its own root and drops it on close - so only the stacking is left to
 * guard against.
 */
let closeOpenPopup: (() => void) | null = null;

/** Opens the dialog. Returns the React root so the dev harness and the tests can unmount it. */
export function openExportPopup(options: OpenExportPopupOptions = {}): Root {
  closeOpenPopup?.();

  const host = document.createElement('div');
  document.body.appendChild(host);
  const container = mountInShadow(host, {
    // `docx-exporter form-wrapper` so the form's own rules match, `sbb-ui` for the design tokens - the same
    // three classes the side panel's container carries.
    containerClassName: 'docx-exporter form-wrapper sbb-ui',
    styleTexts: [popupStyle],
  });

  const location = options.location ?? toDocumentIdentity(currentDocumentLocation());

  const root = createRoot(container);
  const close = () => {
    closeOpenPopup = null;
    root.unmount();
    host.remove();
  };
  closeOpenPopup = close;
  root.render(<ExportPopupModal document={location} onClose={close} deps={options.deps} />);
  return root;
}

export default openExportPopup;
