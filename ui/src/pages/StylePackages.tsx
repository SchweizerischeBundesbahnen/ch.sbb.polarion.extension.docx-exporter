import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ConfigurationButtons,
  ConfigurationsPane,
  type ConfigurationsPaneHandle,
  PageLayout,
  RevisionsTable,
  type SelectOption,
  type SettingName,
  useConfirm,
} from '@sbb-polarion/react-sbb-polarion';
import { toast } from 'sonner';
import StylePackageSettingsView from '../export/StylePackageSettingsView';
import '../export/export-form-layout.css';
import { type ExportForm, childValue as offeredValue, toExportForm } from '../export/exportForm';
import { FieldCell, FieldRow, SwitchRow } from '../export/formRows';
import { getScope } from '../services/scope';
import useNamedSettings from '../services/settings';
import {
  CHILD_SETTINGS,
  type ChildNames,
  type ChildSetting,
  DEFAULT_NAME,
  DEFAULT_WEIGHT,
  MATCHING_QUERY_HELP,
  NO_CHILD_NAMES,
  type StylePackageSettings,
  WEIGHT_HELP,
} from '../services/stylePackage';
import useRemote from '../services/useRemote';

const FEATURE = 'style-package';

/**
 * The form behind the page: the settings an export form shows, plus what only the page has - the weight, the
 * matching query and whether the package exposes its settings. It is not the stored document: a setting the
 * document expresses as "null means not overridden" is two fields here - the checkbox that switches it on and
 * the value it carries - so unticking a box does not throw away what the administrator picked before.
 */
interface Form extends ExportForm {
  matchingQuery: string;
  weight: string;
  exposeSettings: boolean;
}

const EMPTY_FORM: Form = toForm({});

/**
 * The legacy `StylePackageUtils.adjustWeight`, ported unchanged: clamp above 100, keep one decimal,
 * and fall back to 50 for anything that is not `NNN.N` - an empty or nonsense entry included.
 */
function adjustWeight(raw: string): string {
  let value = parseFloat(raw);
  if (value > 100) {
    value = 100;
  }
  if (value % 1 !== 0) {
    value = parseFloat(value.toFixed(1));
  }
  return /^\d{1,3}(\.\d)?$/.test(String(value)) ? String(value) : DEFAULT_WEIGHT;
}

/**
 * A name that belongs to a parent scope is marked the same way `ConfigurationsPane` marks its own
 * options: the shared `inherited` flag, which the dropdown renders as a small italic "global" on the
 * right of the option and turns the names of this scope bold.
 */
function toOption(name: SettingName, scope: string): SelectOption {
  return { id: name.name, name: name.name, inherited: name.scope !== scope };
}

/** The settings of a style package as the export form reads them, without a document to take a language from. */
function toForm(content: StylePackageSettings): Form {
  return {
    ...toExportForm(content),
    matchingQuery: content.matchingQuery ?? '',
    weight: content.weight === null || content.weight === undefined ? DEFAULT_WEIGHT : String(content.weight),
    exposeSettings: !!content.exposeSettings,
  };
}

/**
 * DOCX Exporter: Style Package - the settings one export is driven by, one named configuration at a
 * time. A style package points at configurations of the other settings pages (the reference template,
 * the localization, the webhooks) and carries the switches the conversion itself reads.
 *
 * The names of those child settings are read once, when the page opens. A style package cannot be
 * configured without them, which is why a failure there - or an empty list - is reported as an error
 * rather than as an empty dropdown.
 */
export default function StylePackages() {
  const scope = getScope();
  const settings = useNamedSettings<StylePackageSettings>(FEATURE);
  const { sendRequest } = useRemote();
  const { confirm, confirmDialog } = useConfirm();
  const paneRef = useRef<ConfigurationsPaneHandle>(null);

  /** Which load is the current one; only the newest writes (see Templates for why). */
  const latestLoad = useRef(0);

  const [form, setForm] = useState<Form>(EMPTY_FORM);
  const [selectedConfig, setSelectedConfig] = useState<string | null>(null);
  const [editingName, setEditingName] = useState(false);
  const [showRevisions, setShowRevisions] = useState(false);
  const [revisionsToken, setRevisionsToken] = useState(0);
  const [loadingError, setLoadingError] = useState(false);

  const [childNames, setChildNames] = useState<ChildNames>(NO_CHILD_NAMES);
  const [childNamesError, setChildNamesError] = useState(false);

  const [roleOptions, setRoleOptions] = useState<SelectOption[]>([]);
  const [rolesError, setRolesError] = useState(false);

  /** Whether the installation has webhooks at all; unknown until the status is read. */
  const [webhooksAvailable, setWebhooksAvailable] = useState<boolean | null>(null);

  const patch = (values: Partial<Form>) => setForm((current) => ({ ...current, ...values }));

  useEffect(() => {
    let cancelled = false;
    Promise.all(
      CHILD_SETTINGS.map(async (setting) => {
        const response = await sendRequest({
          method: 'GET',
          url: `/settings/${setting}/names?scope=${encodeURIComponent(scope)}`,
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const names = (await response.json()) as SettingName[];
        // An empty list is a failure too, as it was on the legacy page: a style package has to point at
        // an existing configuration, so there is nothing to choose from and nothing to save.
        if (names.length === 0) throw new Error(`no ${setting} configurations`);
        return [setting, names.map((name) => toOption(name, scope))] as const;
      }),
    )
      .then((entries) => {
        if (cancelled) return;
        setChildNames({ ...NO_CHILD_NAMES, ...Object.fromEntries(entries) } as ChildNames);
        setChildNamesError(false);
      })
      .catch(() => {
        if (!cancelled) setChildNamesError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [sendRequest, scope]);

  useEffect(() => {
    let cancelled = false;
    sendRequest({ method: 'GET', url: `/link-role-names?scope=${encodeURIComponent(scope)}` })
      .then((response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json() as Promise<string[]>;
      })
      .then((names) => {
        if (cancelled) return;
        setRoleOptions(names.map((name) => ({ id: name, name })));
        setRolesError(false);
      })
      .catch(() => {
        if (!cancelled) setRolesError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [sendRequest, scope]);

  // Webhooks are an installation-wide switch, so the row that points at a webhooks configuration is
  // there only when they are on - which the JSP page decided server-side, while it was rendering. A read
  // that fails leaves the row hidden without claiming anything: the stored value is saved back untouched
  // either way, so nothing is lost.
  useEffect(() => {
    let cancelled = false;
    sendRequest({ method: 'GET', url: '/webhooks/status' })
      .then((response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json() as Promise<{ enabled?: boolean }>;
      })
      .then((status) => {
        if (!cancelled) setWebhooksAvailable(!!status?.enabled);
      })
      .catch(() => {
        if (!cancelled) setWebhooksAvailable(false);
      });
    return () => {
      cancelled = true;
    };
  }, [sendRequest]);

  /**
   * The configuration a child dropdown actually points at: a stored name the scope no longer offers falls back
   * to Default, as the export form does - but only once the list is known, so a failed or pending read cannot
   * rewrite a perfectly good reference.
   */
  const childValue = (setting: ChildSetting, value: string): string => offeredValue(childNames[setting], value);

  const applyContent = useCallback((content: StylePackageSettings) => {
    latestLoad.current += 1;
    setForm(toForm(content));
    // A load that succeeded after an earlier failure would otherwise keep the banner up over good data.
    setLoadingError(false);
  }, []);

  const handleSelectedChange = useCallback((name: string | null) => {
    latestLoad.current += 1;
    setSelectedConfig(name);
  }, []);

  const handleSave = async () => {
    if (!selectedConfig) return;
    toast.dismiss();
    // Anything switched off is stored as null rather than as a stale value, which is what makes the
    // checkbox and the stored document agree - the legacy page wrote the very same body.
    const content: StylePackageSettings = {
      matchingQuery: form.matchingQuery,
      weight: Number(adjustWeight(form.weight)),
      exposeSettings: form.exposeSettings,
      template: childValue('templates', form.template),
      localization: childValue('localization', form.localization),
      orientation: form.orientationEnabled ? form.orientation : null,
      paperSize: form.paperSizeEnabled ? form.paperSize : null,
      imageDensity: form.imageDensityEnabled ? form.imageDensity : null,
      preserveTableStyles: form.preserveTableStyles,
      webhooks: form.webhooksEnabled ? childValue('webhooks', form.webhooks) : null,
      renderComments: form.renderCommentsEnabled ? form.renderComments : null,
      includeUnreferencedComments: form.renderCommentsEnabled && form.includeUnreferencedComments,
      cutEmptyChapters: form.cutEmptyChapters,
      cutEmptyWorkitemAttributes: form.cutEmptyWorkitemAttributes,
      cutLocalURLs: form.cutLocalURLs,
      specificChapters: form.specificChaptersEnabled ? form.specificChapters : null,
      language: form.localizeEnums ? form.language : null,
      linkedWorkitemRoles: form.rolesEnabled ? form.linkedWorkitemRoles : null,
      linkRoleDirection: form.rolesEnabled ? form.linkRoleDirection : null,
      removalSelector: form.removalSelector,
    };
    try {
      await settings.saveContent(selectedConfig, scope, content);
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
    setForm(toForm(content));
    setLoadingError(false);
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
      const content = await settings.loadDefaultContent();
      if (seq !== latestLoad.current) return;
      setForm(toForm(content));
      setLoadingError(false);
      toast.success('Default values loaded. Save the data to apply them.');
    } catch {
      setLoadingError(true);
    }
  };

  // The Default style package applies to every document, so it has nothing to match on.
  const matchingQueryShown = selectedConfig !== DEFAULT_NAME;

  return (
    <PageLayout title="DOCX Exporter: Style Package">
      <div className="notifications">
        {loadingError && (
          <div className="alert alert-error">
            Error occurred loading the data. Be sure Polarion is started and accessible.
          </div>
        )}
        {childNamesError && (
          <div className="alert alert-error">
            There was an error loading names of children configurations. Please, contact project/system administrator to
            solve the issue, a style package can&apos;t be configured without them.
          </div>
        )}
        {rolesError && <div className="alert alert-error">There was an error loading link role names.</div>}
      </div>

      <ConfigurationsPane<StylePackageSettings>
        ref={paneRef}
        scope={scope}
        service={settings}
        label="style package"
        cookieKey={`selected-configuration-${FEATURE}`}
        onContentLoaded={applyContent}
        onSelectedChange={handleSelectedChange}
        onEditingNameChange={setEditingName}
      />

      <fieldset className="style-packages-page" disabled={editingName}>
        {/* The rows of the export form, so that a style package reads here as it does where it is used (#423). */}
        <div className="docx-export-form docx-exporter">
          {/* Weight and matching query: what decides the order of the list and which documents see it. */}
          <div className="docx-section single-column group-start">
            <FieldRow
              label={
                <>
                  Weight:
                  <span className="more-info" title={WEIGHT_HELP} />
                </>
              }
              labelFor="style-package-weight"
            >
              <FieldCell>
                <input
                  id="style-package-weight"
                  className="weight-input"
                  type="number"
                  min="1"
                  max="100"
                  step="0.1"
                  value={form.weight}
                  onChange={(e) => patch({ weight: e.target.value })}
                  onBlur={() => patch({ weight: adjustWeight(form.weight) })}
                />
              </FieldCell>
            </FieldRow>
            {matchingQueryShown && (
              <FieldRow
                rowId="matching-query-container"
                label={
                  <>
                    Matching query:
                    <span className="more-info" title={MATCHING_QUERY_HELP} />
                  </>
                }
                labelFor="matching-query"
              >
                <FieldCell grows>
                  <input
                    id="matching-query"
                    type="text"
                    value={form.matchingQuery}
                    onChange={(e) => patch({ matchingQuery: e.target.value })}
                  />
                </FieldCell>
              </FieldRow>
            )}
          </div>

          <div className="docx-section single-column group-start">
            <SwitchRow
              id="exposeSettings"
              label="Expose style package settings to be redefined on UI"
              checked={form.exposeSettings}
              onChange={(checked) => patch({ exposeSettings: checked })}
            />
          </div>

          {/* The settings themselves, as the export dialog and the Document Properties pane show them. */}
          <div className="settings-block group-start">
            <StylePackageSettingsView
              ids=""
              form={form}
              onPatch={patch}
              childNames={childNames}
              roles={roleOptions}
              rolesAlwaysOffered
              webhooksEnabled={!!webhooksAvailable}
              busy={false}
            />
          </div>
        </div>

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
      {confirmDialog}
    </PageLayout>
  );
}
