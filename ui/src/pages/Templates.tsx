import { useCallback, useRef, useState } from 'react';
import {
  ConfigurationButtons,
  ConfigurationsPane,
  type ConfigurationsPaneHandle,
  PageLayout,
  RevisionsTable,
  useConfirm,
} from '@grigoriev/react-sbb-polarion';
import { toast } from 'sonner';
import firstOddEven from '../assets/first_odd_even.png';
import wordDocBlue from '../assets/word_doc_blue.svg';
import wordDocGrey from '../assets/word_doc_grey.svg';
import Placeholders from '../components/Placeholders';
import useDocxTemplate, {
  DOCX_MIME_TYPE,
  type DocxBytes,
  type TemplatesSettings,
  base64ToBytes,
  bytesToBase64,
} from '../services/docxTemplate';
import { saveBlob } from '../services/files';
import { getScope } from '../services/scope';
import useNamedSettings from '../services/settings';

const FEATURE = 'templates';

/** The size of an attached document, in the unit that keeps the number short. */
function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * DOCX Exporter: Templates - the reference document a style package converts against, one named
 * configuration at a time.
 *
 * The whole content of a configuration is a single DOCX file, so the page is one of two panels: an
 * empty one offering the file picker, and a filled one showing what was attached. Neither choosing nor
 * deleting a file writes anything; the configuration is stored on Save, as it was on the JSP page.
 *
 * Nothing about the document is read here. The page reported a style count and a modification date out
 * of the archive - and so needed a zip reader, first JSZip in the browser and then an endpoint on the
 * server - for two facts nobody asked for. It shows the file size instead, which costs a byte count.
 * Whether a file may be stored is decided on Save by `TemplatesSettings`, and its refusal arrives as
 * the message of that failure.
 *
 * The attached document is held as bytes and only as bytes. The legacy page kept a binary string after
 * an upload and a Uint8Array after a load, and its download link built a Blob out of whichever it had -
 * so downloading a file that had just been chosen produced a corrupted DOCX, the string having been
 * UTF-8 encoded on the way into the Blob. One representation, no conversion at the download.
 */
export default function Templates() {
  const scope = getScope();
  const settings = useNamedSettings<TemplatesSettings>(FEATURE);
  const templates = useDocxTemplate();
  const { confirm, confirmDialog } = useConfirm();
  const paneRef = useRef<ConfigurationsPaneHandle>(null);

  /** Which load is the current one; only the newest writes (see Localization for why). */
  const latestLoad = useRef(0);

  const [template, setTemplate] = useState<DocxBytes | null>(null);
  const [selectedConfig, setSelectedConfig] = useState<string | null>(null);
  const [editingName, setEditingName] = useState(false);
  const [showRevisions, setShowRevisions] = useState(false);
  const [revisionsToken, setRevisionsToken] = useState(0);
  const [loadingError, setLoadingError] = useState(false);

  const applyContent = useCallback((content: TemplatesSettings) => {
    latestLoad.current += 1;
    // A load that succeeded after an earlier failure would otherwise keep the banner up over good data.
    setLoadingError(false);
    setTemplate(content.template ? base64ToBytes(content.template) : null);
  }, []);

  /**
   * A new selection invalidates whatever is in flight for the old one, at the moment it is made rather
   * than when the new content lands: a load returning in between would otherwise attach the document of
   * the configuration the administrator has already left.
   */
  const handleSelectedChange = useCallback((name: string | null) => {
    latestLoad.current += 1;
    setSelectedConfig(name);
  }, []);

  const handleFileChosen = async (file: File | undefined) => {
    if (!file) return;
    toast.dismiss();
    const seq = ++latestLoad.current;
    const bytes = new Uint8Array(await file.arrayBuffer());
    if (seq !== latestLoad.current) return;
    setTemplate(bytes);
  };

  const handleDelete = () => {
    latestLoad.current += 1;
    setTemplate(null);
  };

  const handleDownload = () => {
    if (!template || !selectedConfig) return;
    saveBlob(new Blob([template], { type: DOCX_MIME_TYPE }), `Template_${selectedConfig}.docx`);
  };

  const handleDownloadBuiltIn = async () => {
    toast.dismiss();
    try {
      saveBlob(await templates.downloadBuiltInTemplate(), 'Template.docx');
    } catch {
      toast.error('Error downloading the built-in template.');
    }
  };

  const handleSave = async () => {
    if (!selectedConfig) return;
    toast.dismiss();
    try {
      await settings.saveContent(selectedConfig, scope, { template: template ? bytesToBase64(template) : null });
      toast.success('Data successfully saved.');
      await paneRef.current?.reloadNames();
      setRevisionsToken((t) => t + 1);
    } catch (e) {
      toast.error((e as Error).message || 'Error occurred during saving the data.');
    }
  };

  const reload = async (revision?: string) => {
    if (!selectedConfig) return;
    const seq = ++latestLoad.current;
    const content = await settings.loadContent(selectedConfig, scope, revision);
    if (seq !== latestLoad.current) return;
    setLoadingError(false);
    setTemplate(content.template ? base64ToBytes(content.template) : null);
  };

  const handleCancel = async () => {
    if (!(await confirm('Are you sure you want to cancel editing and revert all changes made?'))) return;
    toast.dismiss();
    try {
      await reload();
    } catch {
      setLoadingError(true);
    }
  };

  const handleRevertToDefault = async () => {
    if (!(await confirm('Are you sure you want to return the default value?'))) return;
    toast.dismiss();
    const seq = ++latestLoad.current;
    try {
      // The built-in default of this setting is no template at all, so Default detaches the file.
      const content = await settings.loadDefaultContent();
      if (seq !== latestLoad.current) return;
      setTemplate(content.template ? base64ToBytes(content.template) : null);
      toast.success('Default values loaded. Save the data to apply them.');
    } catch {
      setLoadingError(true);
    }
  };

  return (
    <PageLayout title="DOCX Exporter: Templates">
      <div className="notifications">
        {loadingError && (
          <div className="alert alert-error">
            Error occurred loading the data. Be sure Polarion is started and accessible.
          </div>
        )}
      </div>

      <ConfigurationsPane<TemplatesSettings>
        ref={paneRef}
        scope={scope}
        service={settings}
        cookieKey={`selected-configuration-${FEATURE}`}
        onContentLoaded={applyContent}
        onSelectedChange={handleSelectedChange}
        onEditingNameChange={setEditingName}
      />

      <fieldset className="templates-page" disabled={editingName}>
        <h2 className="align-left">DOCX Template</h2>

        <span className="upload-hint">
          For best results, the reference docx should be a modified version of a docx file built-in into Pandoc (
          <button type="button" className="link-button" onClick={() => void handleDownloadBuiltIn()}>
            download
          </button>
          ).
        </span>

        {template ? (
          <div className="docx-panel">
            <img
              className="docx-download"
              src={wordDocBlue}
              alt="Download the attached template"
              title="Download the attached template"
              role="button"
              tabIndex={0}
              onClick={handleDownload}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  handleDownload();
                }
              }}
            />
            <span className="file-info">File size: {formatFileSize(template.length)}</span>
            <button type="button" className="toolbar-button" title="Delete template" onClick={handleDelete}>
              <span className="button-image sbb-icon-cancel" role="img" aria-label="Cancel" />
              Delete attached file
            </button>
          </div>
        ) : (
          <div className="docx-panel">
            <img src={wordDocGrey} alt="docx" />
            <span>No file provided</span>
            <label className="toolbar-button label" htmlFor="template-file-upload">
              Choose docx template file
            </label>
            <input
              id="template-file-upload"
              type="file"
              accept=".docx"
              aria-label="Choose docx template file"
              onChange={(e) => {
                const file = e.target.files?.[0];
                // Re-selecting the same file must fire `change` again, so the input is cleared.
                e.target.value = '';
                void handleFileChosen(file);
              }}
            />
          </div>
        )}

        <ConfigurationButtons
          onSave={() => void handleSave()}
          onCancel={() => void handleCancel()}
          onRevertToDefault={() => void handleRevertToDefault()}
          onToggleRevisions={() => setShowRevisions((v) => !v)}
          revisionsShown={showRevisions}
        />

        {showRevisions && selectedConfig && (
          <RevisionsTable
            name={selectedConfig}
            scope={scope}
            reloadToken={revisionsToken}
            loadRevisions={settings.loadRevisions}
            onRevert={(revision) => void reload(revision.name)}
          />
        )}
      </fieldset>

      <div className="quick-help">
        <h2 className="align-left">Quick Help</h2>
        <div className="quick-help-text">
          <p>On this page you can add or remove a template docx file applied to selected configuration.</p>

          <h3>What is a template</h3>
          <p>
            A template (also &quot;reference doc&quot; in terms of Pandoc) is a file which is used as a style reference
            in producing a docx.
          </p>

          <h3>How it works</h3>
          <p>
            The contents of the template docx are ignored, but its stylesheets and document properties (including
            margins, paper size, header, and footer) are used in the new docx. If no reference docx is provided then
            pandoc-service will use built-in template (which also can be downloaded from this page using
            &apos;download&apos; link above). Please refer official{' '}
            <a href="https://pandoc.org/MANUAL.html#option--reference-doc" target="_blank" rel="noopener noreferrer">
              pandoc manual
            </a>{' '}
            for more information.
          </p>
          <p>
            Header and footer parts can contain dynamic elements which will be evaluated before DOCX generation, e.g.:
            $document.getId() for velocity expressions, {'{{ DOCUMENT_TITLE }}'} for special variables or{' '}
            {'{{ docRevision }}'} for document&apos;s custom fields.
          </p>
          <Placeholders />

          <h3>Different first, odd and even pages</h3>
          <p>
            Headers and footers can be configured differently for the first, odd and even pages - changes made in an
            editor will be applied to the resulting document:
          </p>
          <img className="admin-page-screenshot" src={firstOddEven} alt="first, odd and even pages" />
        </div>
      </div>
      {confirmDialog}
    </PageLayout>
  );
}
