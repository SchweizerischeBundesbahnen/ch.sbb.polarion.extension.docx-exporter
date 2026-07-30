import { useCallback, useEffect, useRef, useState } from 'react';
import {
  CodeEditor,
  ConfigurationButtons,
  PageLayout,
  RevisionsTable,
  useConfirm,
} from '@grigoriev/react-sbb-polarion';
import { toast } from 'sonner';
import Placeholders from '../components/Placeholders';
import { getScope } from '../services/scope';
import useNamedSettings from '../services/settings';

/** Content of the `filename-template` setting: this exporter names documents from one template. */
interface FilenameTemplateSettings {
  documentNameTemplate: string;
}

const FEATURE = 'filename-template';
/** The single always-present setting of a feature without named configurations. */
const DEFAULT_NAME = 'Default';

/**
 * DOCX Exporter: Filename template - the Velocity template the exported file is named after.
 *
 * A single setting, so no configuration selector; and unlike the PDF exporter's four templates this
 * one has no opt-in checkbox and no read-only "default" tab - it has a Default button instead, which
 * loads the built-in template into the editor for saving. Both of those are how the JSP page behaved.
 */
export default function FilenameTemplate() {
  const scope = getScope();
  const settings = useNamedSettings<FilenameTemplateSettings>(FEATURE);
  const { confirm, confirmDialog } = useConfirm();

  const [template, setTemplate] = useState('');
  /**
   * Which load is the current one. Every path that fills the editor - the initial load, Cancel,
   * Default, reverting to a revision - is a request the administrator can outrun by typing or by
   * starting another one, and the slowest response would otherwise land last and win. Only the
   * newest request writes.
   */
  const latestLoad = useRef(0);
  const [showRevisions, setShowRevisions] = useState(false);
  const [revisionsToken, setRevisionsToken] = useState(0);
  const [loadingError, setLoadingError] = useState(false);

  const load = useCallback(
    async (revision?: string) => {
      const seq = ++latestLoad.current;
      const content = await settings.loadContent(DEFAULT_NAME, scope, revision);
      if (seq !== latestLoad.current) return;
      setTemplate(content.documentNameTemplate ?? '');
      // Whatever failed before has now succeeded; the banner would otherwise stay up over good data.
      setLoadingError(false);
    },
    [settings, scope],
  );

  useEffect(() => {
    let cancelled = false;
    setLoadingError(false);
    load().catch(() => {
      if (!cancelled) setLoadingError(true);
    });
    return () => {
      cancelled = true;
    };
  }, [load]);

  const handleSave = async () => {
    toast.dismiss();
    try {
      await settings.saveContent(DEFAULT_NAME, scope, { documentNameTemplate: template });
      toast.success('Data successfully saved.');
      setRevisionsToken((t) => t + 1);
    } catch (e) {
      toast.error((e as Error).message || 'Error occurred during saving the data.');
    }
  };

  const handleCancel = async () => {
    if (!(await confirm('Are you sure you want to cancel editing and revert all changes made?'))) return;
    toast.dismiss();
    try {
      await load();
    } catch {
      setLoadingError(true);
    }
  };

  const handleRevertToDefault = async () => {
    if (!(await confirm('Are you sure you want to return the default values?'))) return;
    toast.dismiss();
    try {
      const seq = ++latestLoad.current;
      const content = await settings.loadDefaultContent();
      if (seq !== latestLoad.current) return;
      setTemplate(content.documentNameTemplate ?? '');
      setLoadingError(false);
      toast.success('Reverted to the default values. Remember to save the configuration.');
    } catch {
      setLoadingError(true);
    }
  };

  return (
    <PageLayout title="DOCX Exporter: Filename template">
      <div className="notifications">
        {loadingError && (
          <div className="alert alert-error">
            Error occurred loading the data. Be sure Polarion is started and accessible.
          </div>
        )}
      </div>

      <div className="filename-template-page">
        <div className="label-block">
          <label htmlFor="document-name-template">Document filename template:</label>
        </div>
        <CodeEditor
          language="velocity"
          id="document-name-template"
          className="filename-template-editor"
          value={template}
          onChange={setTemplate}
        />

        <ConfigurationButtons
          onSave={() => void handleSave()}
          onCancel={() => void handleCancel()}
          onRevertToDefault={() => void handleRevertToDefault()}
          onToggleRevisions={() => setShowRevisions((v) => !v)}
          revisionsShown={showRevisions}
        />

        {showRevisions && (
          <RevisionsTable
            name={DEFAULT_NAME}
            scope={scope}
            reloadToken={revisionsToken}
            loadRevisions={settings.loadRevisions}
            onRevert={(revision) => void load(revision.name)}
          />
        )}
      </div>

      <div className="help">
        <h2 className="align-left">Quick Help</h2>
        <h3>How to configure Filename template</h3>
        <p>
          Filenames can be made scriptable by incorporating placeholders and velocity code. These filenames can contain
          Velocity expressions that are dynamically evaluated, allowing for the inclusion of dynamic values.
        </p>
        <p>For example: {'{{ PROJECT_NAME }}'} $page.spaceId $page.titleOrName $page.lastRevision</p>
        <Placeholders />
      </div>
      {confirmDialog}
    </PageLayout>
  );
}
