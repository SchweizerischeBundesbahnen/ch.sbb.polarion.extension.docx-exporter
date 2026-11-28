import { useCallback, useEffect, useState } from 'react';
import {
  ConfigurationButtons,
  PageLayout,
  type Revision,
  RevisionsTable,
  useConfirm,
} from '@sbb-polarion/react-sbb-polarion';
import { toast } from 'sonner';
import { getScope } from '../services/scope';
import useNamedSettings from '../services/settings';

/** The named-settings feature the button configuration is stored under. */
const FEATURE = 'appearance';

/** The single setting name the generic framework uses when a feature has no named configurations. */
const DEFAULT_NAME = 'Default';

/** Content of the `appearance` setting, as the backend's AppearanceModel serializes it. */
interface AppearanceContent {
  liveDocButton?: boolean;
}

/** What the page edits: the same flags, each resolved to a value the checkboxes can bind to. */
interface AppearanceState {
  liveDocButton: boolean;
}

const ALL_BUTTONS: AppearanceState = {
  liveDocButton: true,
};

function toState(content: AppearanceContent): AppearanceState {
  return {
    liveDocButton: content.liveDocButton ?? ALL_BUTTONS.liveDocButton,
  };
}

/**
 * DOCX Exporter: Appearance - whether the extension puts its "Export to DOCX" button on a Polarion page.
 *
 * One setting named `Default` per scope. A project without one reads the Default Repository setting,
 * which is what the settings framework falls back to, so the Default Repository configures every
 * project at once. A project overrides it by saving its own, and goes back to inheriting by ticking
 * "Use the configuration from the Default Repository", which deletes it.
 */
export default function Appearance() {
  const scope = getScope();
  const projectScope = scope !== '';
  const settings = useNamedSettings<AppearanceContent>(FEATURE);
  const { confirm, confirmDialog } = useConfirm();

  const [state, setState] = useState<AppearanceState>(ALL_BUTTONS);
  const [inherited, setInherited] = useState(false);
  const [hasOwnSetting, setHasOwnSetting] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [loadingError, setLoadingError] = useState(false);
  const [showRevisions, setShowRevisions] = useState(false);
  const [revisionsToken, setRevisionsToken] = useState(0);

  /**
   * Whether this scope stores a setting of its own. `readNames` answers with one `Default` entry
   * whose `scope` is the scope the file was found in, so the entry naming this scope is the
   * project's own file and anything else is the Default Repository's.
   */
  const readOwnership = useCallback(async (): Promise<boolean> => {
    if (!projectScope) {
      return true;
    }
    const names = await settings.loadConfigurationNames(scope);
    return names.some((name) => name.name === DEFAULT_NAME && name.scope === scope);
  }, [settings, scope, projectScope]);

  const load = useCallback(
    async (revision?: string) => {
      // The content read for a project scope already falls back to the Default Repository, so the
      // inherited values are what the controls show while "use the global configuration" is ticked.
      const [own, content] = await Promise.all([readOwnership(), settings.loadContent(DEFAULT_NAME, scope, revision)]);
      setHasOwnSetting(own);
      setInherited(!own);
      setState(toState(content));
      setLoadingError(false);
    },
    [readOwnership, settings, scope],
  );

  useEffect(() => {
    let cancelled = false;
    load()
      .catch(() => {
        if (!cancelled) setLoadingError(true);
      })
      .finally(() => {
        if (!cancelled) setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, [load]);

  const patch = (change: Partial<AppearanceState>) => setState((current) => ({ ...current, ...change }));

  const handleSave = async () => {
    toast.dismiss();
    try {
      if (inherited) {
        // Inheriting is the absence of a setting in this scope, so it is stored by deleting the
        // project's own file rather than by writing a flag into it.
        if (hasOwnSetting) {
          await settings.deleteConfiguration(DEFAULT_NAME, scope);
        }
        await load();
        toast.success('This project now uses the configuration of the Default Repository.');
      } else {
        await settings.saveContent(DEFAULT_NAME, scope, state);
        setHasOwnSetting(true);
        toast.success('Data successfully saved.');
      }
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
      setState(toState(await settings.loadDefaultContent()));
      setInherited(false);
      toast.success('Default values loaded. Save the data to apply them.');
    } catch {
      setLoadingError(true);
    }
  };

  const handleRevertToRevision = async (revision: Revision) => {
    toast.dismiss();
    try {
      setState(toState(await settings.loadContent(DEFAULT_NAME, scope, revision.name)));
      setInherited(false);
      toast.success(`Reverted to revision ${revision.name}. Remember to save the configuration.`);
    } catch {
      setLoadingError(true);
    }
  };

  if (!loaded) {
    return (
      <PageLayout title="DOCX Exporter: Appearance">
        <p>Loading...</p>
      </PageLayout>
    );
  }

  return (
    <PageLayout title="DOCX Exporter: Appearance">
      <div className="notifications">
        {loadingError && (
          <div className="alert alert-error">
            Error occurred loading the data. Be sure Polarion is started and accessible.
          </div>
        )}
      </div>

      <div className="appearance-page">
        {projectScope && (
          <label className="appearance-inherit" htmlFor="appearance-inherit">
            <input
              id="appearance-inherit"
              type="checkbox"
              checked={inherited}
              onChange={(e) => setInherited(e.target.checked)}
            />
            Use the configuration from the Default Repository
          </label>
        )}

        <fieldset className="appearance-options" disabled={inherited}>
          <h2 className="align-left">Document editor</h2>
          <label htmlFor="live-doc-button">
            <input
              id="live-doc-button"
              type="checkbox"
              checked={state.liveDocButton}
              onChange={(e) => patch({ liveDocButton: e.target.checked })}
            />
            Display the &quot;Export to DOCX&quot; toolbar button
          </label>
        </fieldset>

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
            onRevert={(revision) => void handleRevertToRevision(revision)}
          />
        )}
      </div>

      {confirmDialog}

      <div className="quick-help">
        <h2 className="align-left">Quick Help</h2>
        <div className="quick-help-text">
          <p>
            Here you choose whether the extension puts its &quot;Export to DOCX&quot; button into the document editor
            toolbar. A button which is switched off is not drawn at all; the export itself stays available through the
            Document Properties side panel and the REST API.
          </p>
          <h3>Scope</h3>
          <p>
            What you save in the Default Repository applies to every project which has no configuration of its own. A
            project administrator overrides it on this page in the project, and returns to the global configuration by
            ticking &quot;Use the configuration from the Default Repository&quot; and saving.
          </p>
        </div>
      </div>
    </PageLayout>
  );
}
