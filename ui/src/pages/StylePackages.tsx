import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ConfigurationButtons,
  ConfigurationsPane,
  type ConfigurationsPaneHandle,
  PageLayout,
  RevisionsTable,
  SearchableSelect,
  type SelectOption,
  type SettingName,
  useConfirm,
} from '@sbb-polarion/react-sbb-polarion';
import { toast } from 'sonner';
import { getScope } from '../services/scope';
import useNamedSettings from '../services/settings';
import {
  CHILD_SETTINGS,
  COMMENTS_RENDER_TYPES,
  type ChildNames,
  type ChildSetting,
  DEFAULT_IMAGE_DENSITY,
  DEFAULT_LANGUAGE,
  DEFAULT_LINK_ROLE_DIRECTION,
  DEFAULT_NAME,
  DEFAULT_ORIENTATION,
  DEFAULT_PAPER_SIZE,
  DEFAULT_RENDER_COMMENTS,
  DEFAULT_WEIGHT,
  IMAGE_DENSITIES,
  LANGUAGES,
  LINK_ROLE_DIRECTIONS,
  MATCHING_QUERY_HELP,
  NO_CHILD_NAMES,
  ORIENTATIONS,
  PAPER_SIZES,
  REMOVAL_SELECTOR_HELP,
  type StylePackageSettings,
  UNREFERENCED_COMMENTS_HELP,
  WEIGHT_HELP,
} from '../services/stylePackage';
import useRemote from '../services/useRemote';

const FEATURE = 'style-package';

/**
 * The form behind the page. It is not the stored document: a setting the document expresses as "null
 * means not overridden" is two fields here - the checkbox that switches it on and the value it carries
 * - so unticking a box does not throw away what the administrator picked before ticking it again.
 */
interface Form {
  matchingQuery: string;
  weight: string;
  exposeSettings: boolean;
  template: string;
  localization: string;
  orientationEnabled: boolean;
  orientation: string;
  paperSizeEnabled: boolean;
  paperSize: string;
  imageDensityEnabled: boolean;
  imageDensity: string;
  preserveTableStyles: boolean;
  webhooksEnabled: boolean;
  webhooks: string;
  renderCommentsEnabled: boolean;
  renderComments: string;
  includeUnreferencedComments: boolean;
  cutEmptyChapters: boolean;
  cutEmptyWorkitemAttributes: boolean;
  cutLocalURLs: boolean;
  specificChaptersEnabled: boolean;
  specificChapters: string;
  localizeEnums: boolean;
  language: string;
  rolesEnabled: boolean;
  linkedWorkitemRoles: string[];
  linkRoleDirection: string;
  removalSelector: string;
}

const EMPTY_FORM: Form = {
  matchingQuery: '',
  weight: DEFAULT_WEIGHT,
  exposeSettings: false,
  template: DEFAULT_NAME,
  localization: DEFAULT_NAME,
  orientationEnabled: false,
  orientation: DEFAULT_ORIENTATION,
  paperSizeEnabled: false,
  paperSize: DEFAULT_PAPER_SIZE,
  imageDensityEnabled: false,
  imageDensity: DEFAULT_IMAGE_DENSITY,
  preserveTableStyles: false,
  webhooksEnabled: false,
  webhooks: DEFAULT_NAME,
  renderCommentsEnabled: false,
  renderComments: DEFAULT_RENDER_COMMENTS,
  includeUnreferencedComments: false,
  cutEmptyChapters: false,
  cutEmptyWorkitemAttributes: false,
  cutLocalURLs: false,
  specificChaptersEnabled: false,
  specificChapters: '',
  localizeEnums: false,
  language: DEFAULT_LANGUAGE,
  rolesEnabled: false,
  linkedWorkitemRoles: [],
  linkRoleDirection: DEFAULT_LINK_ROLE_DIRECTION,
  removalSelector: '',
};

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

function toForm(content: StylePackageSettings): Form {
  const roles = content.linkedWorkitemRoles ?? [];
  return {
    matchingQuery: content.matchingQuery ?? '',
    weight: content.weight === null || content.weight === undefined ? DEFAULT_WEIGHT : String(content.weight),
    exposeSettings: !!content.exposeSettings,
    template: content.template ?? DEFAULT_NAME,
    localization: content.localization ?? DEFAULT_NAME,
    orientationEnabled: !!content.orientation,
    orientation: content.orientation ?? DEFAULT_ORIENTATION,
    paperSizeEnabled: !!content.paperSize,
    paperSize: content.paperSize ?? DEFAULT_PAPER_SIZE,
    imageDensityEnabled: !!content.imageDensity,
    imageDensity: content.imageDensity ?? DEFAULT_IMAGE_DENSITY,
    preserveTableStyles: !!content.preserveTableStyles,
    webhooksEnabled: !!content.webhooks,
    webhooks: content.webhooks ?? DEFAULT_NAME,
    renderCommentsEnabled: !!content.renderComments,
    renderComments: content.renderComments ?? DEFAULT_RENDER_COMMENTS,
    includeUnreferencedComments: !!content.includeUnreferencedComments,
    cutEmptyChapters: !!content.cutEmptyChapters,
    cutEmptyWorkitemAttributes: !!content.cutEmptyWorkitemAttributes,
    cutLocalURLs: !!content.cutLocalURLs,
    specificChaptersEnabled: !!content.specificChapters,
    specificChapters: content.specificChapters ?? '',
    localizeEnums: !!content.language,
    language: content.language ?? DEFAULT_LANGUAGE,
    rolesEnabled: roles.length > 0,
    linkedWorkitemRoles: roles,
    linkRoleDirection: content.linkRoleDirection ?? DEFAULT_LINK_ROLE_DIRECTION,
    removalSelector: content.removalSelector ?? '',
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
   * The configuration a child dropdown actually points at. A stored name that the scope no longer
   * offers falls back to Default, exactly as the legacy page did - but only once the list is known, so
   * a failed or pending read cannot rewrite a perfectly good reference.
   */
  const childValue = useCallback(
    (setting: ChildSetting, value: string): string => {
      const options = childNames[setting];
      if (options.length === 0 || options.some((option) => option.id === value)) {
        return value;
      }
      return DEFAULT_NAME;
    },
    [childNames],
  );

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

  /** A value control that is switched off keeps its place in the layout instead of collapsing the row. */
  const reserved = (shown: boolean) => (shown ? '' : ' hidden');

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
        <div className="flex-container section">
          <div className="flex-column">
            <div className="input-group flex-centered">
              <label htmlFor="style-package-weight">Weight:</label>
              <div className="more-info" title={WEIGHT_HELP} />
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
            </div>
          </div>

          {matchingQueryShown && (
            <div className="flex-grow">
              <div className="input-group flex-centered">
                <label htmlFor="matching-query">Matching query:</label>
                <div className="more-info" title={MATCHING_QUERY_HELP} />
                <input
                  id="matching-query"
                  className="matching-query-input grows"
                  type="text"
                  value={form.matchingQuery}
                  onChange={(e) => patch({ matchingQuery: e.target.value })}
                />
              </div>
            </div>
          )}
        </div>

        <div className="flex-container section">
          <div className="flex-column">
            <div className="checkbox input-group">
              <label htmlFor="exposeSettings">
                <input
                  id="exposeSettings"
                  type="checkbox"
                  checked={form.exposeSettings}
                  onChange={(e) => patch({ exposeSettings: e.target.checked })}
                />
                Expose style package settings to be redefined on UI
              </label>
            </div>
          </div>
        </div>

        <div className="flex-container">
          <div className="flex-column">
            <div className="input-group">
              <label htmlFor="template-select">Template:</label>
              <SearchableSelect
                id="template-select"
                options={childNames.templates}
                value={childValue('templates', form.template)}
                onChange={(value) => patch({ template: value })}
              />
            </div>
          </div>
        </div>

        <div className="flex-container">
          <div className="flex-column">
            <div className="input-group">
              <label htmlFor="localization-select">Localization:</label>
              <SearchableSelect
                id="localization-select"
                options={childNames.localization}
                value={childValue('localization', form.localization)}
                onChange={(value) => patch({ localization: value })}
              />
            </div>
          </div>
        </div>

        <div className="flex-container">
          <div className="flex-column">
            <div className="checkbox input-group with-value">
              <label htmlFor="orientation">
                <input
                  id="orientation"
                  type="checkbox"
                  checked={form.orientationEnabled}
                  onChange={(e) => patch({ orientationEnabled: e.target.checked })}
                />
                Custom orientation
              </label>
              <div className={`value-select${reserved(form.orientationEnabled)}`}>
                <SearchableSelect
                  id="orientation-select"
                  options={ORIENTATIONS}
                  value={form.orientation}
                  disabled={!form.orientationEnabled}
                  onChange={(value) => patch({ orientation: value })}
                />
              </div>
            </div>
          </div>
        </div>

        <div className="flex-container">
          <div className="flex-column">
            <div className="checkbox input-group with-value">
              <label htmlFor="paper-size">
                <input
                  id="paper-size"
                  type="checkbox"
                  checked={form.paperSizeEnabled}
                  onChange={(e) => patch({ paperSizeEnabled: e.target.checked })}
                />
                Custom paper size
              </label>
              <div className={`value-select${reserved(form.paperSizeEnabled)}`}>
                <SearchableSelect
                  id="paper-size-select"
                  options={PAPER_SIZES}
                  value={form.paperSize}
                  disabled={!form.paperSizeEnabled}
                  onChange={(value) => patch({ paperSize: value })}
                />
              </div>
            </div>
          </div>
        </div>

        <div className="flex-container">
          <div className="flex-column">
            <div className="checkbox input-group with-value">
              <label htmlFor="image-density">
                <input
                  id="image-density"
                  type="checkbox"
                  checked={form.imageDensityEnabled}
                  onChange={(e) => patch({ imageDensityEnabled: e.target.checked })}
                />
                Custom image density
              </label>
              <div className={`value-select${reserved(form.imageDensityEnabled)}`}>
                <SearchableSelect
                  id="image-density-select"
                  options={IMAGE_DENSITIES}
                  value={form.imageDensity}
                  disabled={!form.imageDensityEnabled}
                  onChange={(value) => patch({ imageDensity: value })}
                />
              </div>
            </div>
          </div>
        </div>

        <div className="flex-container">
          <div className="flex-column">
            <div className="checkbox input-group">
              <label htmlFor="preserve-table-styles">
                <input
                  id="preserve-table-styles"
                  type="checkbox"
                  checked={form.preserveTableStyles}
                  onChange={(e) => patch({ preserveTableStyles: e.target.checked })}
                />
                Preserve table styles
              </label>
            </div>
          </div>
        </div>

        {webhooksAvailable && (
          <div className="flex-container section">
            <div className="flex-column">
              <div className="input-group checkbox">
                <label className="webhooks-label" htmlFor="webhooks-checkbox">
                  <input
                    id="webhooks-checkbox"
                    type="checkbox"
                    checked={form.webhooksEnabled}
                    onChange={(e) => patch({ webhooksEnabled: e.target.checked })}
                  />
                  Use webhooks
                </label>
                {form.webhooksEnabled && (
                  <SearchableSelect
                    id="webhooks-select"
                    options={childNames.webhooks}
                    value={childValue('webhooks', form.webhooks)}
                    onChange={(value) => patch({ webhooks: value })}
                  />
                )}
              </div>
            </div>
          </div>
        )}

        <div className="flex-container">
          <div className="flex-column">
            <div className="checkbox input-group with-value">
              <label htmlFor="render-comments">
                <input
                  id="render-comments"
                  type="checkbox"
                  checked={form.renderCommentsEnabled}
                  onChange={(e) =>
                    patch({
                      renderCommentsEnabled: e.target.checked,
                      // Switching comments off takes the sub-option with it, as the legacy page did.
                      includeUnreferencedComments: e.target.checked && form.includeUnreferencedComments,
                    })
                  }
                />
                Comments rendering
              </label>
              <div className={`value-select${reserved(form.renderCommentsEnabled)}`}>
                <SearchableSelect
                  id="render-comments-select"
                  options={COMMENTS_RENDER_TYPES}
                  value={form.renderComments}
                  disabled={!form.renderCommentsEnabled}
                  onChange={(value) => patch({ renderComments: value })}
                />
              </div>
            </div>
            {form.renderCommentsEnabled && (
              <div className="checkbox input-group render-comments-options">
                <label htmlFor="include-unreferenced-comments" title={UNREFERENCED_COMMENTS_HELP}>
                  <input
                    id="include-unreferenced-comments"
                    type="checkbox"
                    checked={form.includeUnreferencedComments}
                    onChange={(e) => patch({ includeUnreferencedComments: e.target.checked })}
                  />
                  include unreferenced
                </label>
              </div>
            )}
            <div className="checkbox input-group">
              <label htmlFor="cut-empty-wi-attributes">
                <input
                  id="cut-empty-wi-attributes"
                  type="checkbox"
                  checked={form.cutEmptyWorkitemAttributes}
                  onChange={(e) => patch({ cutEmptyWorkitemAttributes: e.target.checked })}
                />
                Cut empty Workitem attributes
              </label>
            </div>
          </div>
          <div className="flex-column">
            <div className="checkbox input-group">
              <label htmlFor="cut-empty-chapters">
                <input
                  id="cut-empty-chapters"
                  type="checkbox"
                  checked={form.cutEmptyChapters}
                  onChange={(e) => patch({ cutEmptyChapters: e.target.checked })}
                />
                Cut empty chapters (any level)
              </label>
            </div>
            <div className="checkbox input-group">
              <label htmlFor="cut-urls">
                <input
                  id="cut-urls"
                  type="checkbox"
                  checked={form.cutLocalURLs}
                  onChange={(e) => patch({ cutLocalURLs: e.target.checked })}
                />
                Cut local Polarion URLs
              </label>
            </div>
          </div>
        </div>

        <div className="flex-container">
          <div className="flex-column">
            <div className="checkbox input-group with-value">
              <label htmlFor="specific-chapters">
                <input
                  id="specific-chapters"
                  type="checkbox"
                  checked={form.specificChaptersEnabled}
                  onChange={(e) => patch({ specificChaptersEnabled: e.target.checked })}
                />
                Specific higher level chapters
              </label>
              <input
                id="chapters"
                className={`grows${reserved(form.specificChaptersEnabled)}`}
                type="text"
                placeholder="eg. 1,2,4 etc."
                disabled={!form.specificChaptersEnabled}
                value={form.specificChapters}
                onChange={(e) => patch({ specificChapters: e.target.value })}
              />
            </div>
            <div className="checkbox input-group roles-group">
              <label htmlFor="selected-roles">
                <input
                  id="selected-roles"
                  type="checkbox"
                  checked={form.rolesEnabled}
                  onChange={(e) => patch({ rolesEnabled: e.target.checked })}
                />
                Specific Workitem roles
              </label>
              {form.rolesEnabled && (
                <>
                  <div className="roles-select">
                    <SearchableSelect
                      id="roles-select"
                      multiple
                      options={roleOptions}
                      value={form.linkedWorkitemRoles}
                      onChange={(value) => patch({ linkedWorkitemRoles: value })}
                    />
                  </div>
                  <div className="roles-select">
                    <SearchableSelect
                      id="link-role-direction-select"
                      options={LINK_ROLE_DIRECTIONS}
                      value={form.linkRoleDirection}
                      onChange={(value) => patch({ linkRoleDirection: value })}
                    />
                  </div>
                </>
              )}
            </div>
          </div>
          <div className="flex-column">
            <div className="checkbox input-group with-value">
              <label htmlFor="localization">
                <input
                  id="localization"
                  type="checkbox"
                  checked={form.localizeEnums}
                  onChange={(e) => patch({ localizeEnums: e.target.checked })}
                />
                Localize enums
              </label>
              <div className={`value-select${reserved(form.localizeEnums)}`}>
                <SearchableSelect
                  id="language-select"
                  options={LANGUAGES}
                  value={form.language}
                  disabled={!form.localizeEnums}
                  onChange={(value) => patch({ language: value })}
                />
              </div>
            </div>
          </div>
        </div>

        <div className="flex-container">
          <div className="input-group flex-centered removal-selector-group">
            <label htmlFor="removal-selector-input">Removal selector:</label>
            <div className="more-info" title={REMOVAL_SELECTOR_HELP} />
            <input
              id="removal-selector-input"
              className="grows"
              type="text"
              value={form.removalSelector}
              onChange={(e) => patch({ removalSelector: e.target.value })}
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
