import { useCallback, useState } from 'react';
import { PageLayout } from '@sbb-polarion/react-sbb-polarion';
import DocumentPicker from '../components/DocumentPicker';
import { documentEditorHash } from '../services/documents';
import type { ProjectDocument } from '../services/documents';

/**
 * Development harness for the "Export to DOCX" dialog.
 *
 * It opens the dialog through the **real** `openExportPopup` path - shadow-root mounted, the same way the
 * document editor toolbar opens it. The harness writes the Polarion editor hash the document is opened at,
 * so the dialog reads its location out of the URL exactly as it does in Polarion and every REST call goes to
 * the Polarion behind `VITE_BASE_URL`.
 *
 * That is the one thing the Vitest suites cannot cover - a real page URL and the real endpoints behind it.
 * The dialog's own states are covered offline and pixel-locked by `test/ExportPopup.visual.test.tsx`.
 */
export default function ExportPopupPreview() {
  const [picked, setPicked] = useState<{ projectId: string; document: ProjectDocument } | undefined>();

  const onPick = useCallback((next: { projectId: string; document: ProjectDocument } | undefined) => {
    setPicked(next);
  }, []);

  const open = () => {
    if (!picked) {
      return;
    }
    // `history.replaceState` keeps `?feature=` and `?scope=` intact - the app routes on the search
    // parameters, not on the hash.
    const hash = documentEditorHash(picked.projectId, picked.document);
    window.history.replaceState({}, '', `${window.location.pathname}${window.location.search}${hash}`);

    // Imported here rather than at the top of the file on purpose: a static import would put the whole popup
    // bundle into the admin app's own entry, which every administration page then loads for the sake of this
    // development page.
    void import('../popup/mount').then(({ openExportPopup }) => openExportPopup());
  };

  return (
    <PageLayout title="DOCX Exporter: Export to DOCX dialog (dev harness)">
      <p className="landing-intro">
        The export dialog as the document editor toolbar button opens it, through the real <code>openExportPopup</code>{' '}
        path (shadow-root mounted) against a real document: the harness writes the editor hash the document is opened
        at, and the dialog reads it. Needs a Polarion behind <code>VITE_BASE_URL</code>.
      </p>

      <DocumentPicker onChange={onPick} />

      <div className="preview-controls">
        <button type="button" disabled={!picked} onClick={open}>
          Open Export to DOCX
        </button>
      </div>
    </PageLayout>
  );
}
