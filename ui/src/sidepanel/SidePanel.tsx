import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { SearchableSelect } from '@grigoriev/react-sbb-polarion';
import type { SelectOption } from '@grigoriev/react-sbb-polarion';
import type { PanelData } from '../export/exportData';
import { loadPanelData, loadStylePackage } from '../export/exportData';
import type { ExportForm } from '../export/exportForm';
import { childValue, toExportForm } from '../export/exportForm';
import type { ExportField } from '../export/exportParams';
import { buildExportParams, toRequestBody } from '../export/exportParams';
import { convertDocx, downloadBlob } from '../services/conversion';
import type { DocumentIdentity } from '../services/exportContext';
import { currentDocumentLocation, toDocumentIdentity } from '../services/exportContext';
import {
  COMMENTS_RENDER_TYPES,
  IMAGE_DENSITIES,
  LANGUAGES,
  LINK_ROLE_DIRECTIONS,
  ORIENTATIONS,
  PAPER_SIZES,
  REMOVAL_SELECTOR_HELP,
  type StylePackageSettings,
  UNREFERENCED_COMMENTS_HELP,
} from '../services/stylePackage';
import useRemote from '../services/useRemote';

/** Polarion's own Word roundtrip icon, served by the platform - the icon the legacy panel used. */
const EXPORT_ICON = '/polarion/ria/images/dle/operations/actionMsWordRoundtrip16.svg';

const PACKAGE_LOAD_ERROR = 'There was an error loading style package settings. Please, contact administrator';

/**
 * What the panel says while it reads what it offers.
 *
 * Deliberately generic: this one state covers seven parallel reads, so naming any of them would say
 * less than nothing. `Loading...` is the wording the other extensions' loading states use.
 */
const LOADING_MESSAGE = 'Loading...';

const NOT_AUTHORIZED = 'You are not allowed to export DOCX for this project';

/**
 * Why the export button is off when the permission could not be read at all. Both cases keep it off -
 * the check fails closed - but only an explicit refusal can be reported as one.
 */
const PERMISSION_UNKNOWN = 'Could not check whether you are allowed to export. Please, reload the page.';

/** What the panel reaches outside itself for, so the dev harness and the tests can replace it. */
export interface SidePanelDependencies {
  /** Where the document is. Read from the editor URL when not given, which is what happens in Polarion. */
  location?: DocumentIdentity;
  loadData?: typeof loadPanelData;
  loadPackage?: typeof loadStylePackage;
  convert?: typeof convertDocx;
  download?: typeof downloadBlob;
}

export interface SidePanelProps {
  deps?: SidePanelDependencies;
}

/** `<prefix>` on its own line, then the detail - the legacy `prefix + ":<br>" + message`. */
const withDetail = (prefix: string, detail: string): string => (detail ? `${prefix}:\n${detail}` : prefix);

/** What a rejected conversion says, which is the server's message or nothing. */
const messageOf = (error: unknown): string => (error instanceof Error ? error.message : '');

/**
 * DOCX Exporter's Document Properties side panel: the React port of `sidePanelContent.html` +
 * `ExportPanel.js`.
 *
 * It is mounted by `mountSidePanel` into a shadow root on the fragment div Polarion injects into the
 * document editor's Document Properties pane. `side-panel.css` - the panel's half of the legacy page
 * stylesheet, injected into the same shadow root - styles it, so the panel looks exactly as it did.
 *
 * What did change is where the data comes from. `DocxExporterFormExtension` used to render this markup
 * with the style packages, setting names, link roles, file name and export permission already
 * substituted into it; now those are read over REST. The document location and the conversion protocol
 * used to come from the product's `ExportContext.js`, loaded at runtime from the other webapp; both are
 * `services/exportContext.ts` and `services/conversion.ts` now, which this app owns.
 */
export default function SidePanel({ deps }: Readonly<SidePanelProps>) {
  const { sendRequest, sendAbsoluteRequest } = useRemote();
  const loadData = deps?.loadData ?? loadPanelData;
  const loadPackage = deps?.loadPackage ?? loadStylePackage;
  const convert = deps?.convert ?? convertDocx;
  const download = deps?.download ?? downloadBlob;

  const remote = useMemo(() => ({ sendRequest, sendAbsoluteRequest }), [sendRequest, sendAbsoluteRequest]);

  const [data, setData] = useState<PanelData | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [stylePackage, setStylePackage] = useState('');
  /** Whether the selected package invites the user to redefine its settings. */
  const [exposeSettings, setExposeSettings] = useState(false);
  const [form, setForm] = useState<ExportForm | null>(null);
  const [loadingPackage, setLoadingPackage] = useState(false);

  const [fileName, setFileName] = useState('');
  const [invalidField, setInvalidField] = useState<ExportField | null>(null);

  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [exportWarning, setExportWarning] = useState<string | null>(null);

  /** Which package load is the current one; a slower earlier one must not overwrite it. */
  const latestPackage = useRef(0);

  /** Where the document lives, read out of the editor URL the way the product's ExportContext read it. */
  const document_: DocumentIdentity = useMemo(
    () => deps?.location ?? toDocumentIdentity(currentDocumentLocation()),
    [deps?.location],
  );

  // Everything the panel offers. The style packages and the option lists are required - there is nothing
  // to choose from without them - so a failure here is reported instead of an empty panel.
  useEffect(() => {
    let cancelled = false;
    loadData(sendRequest, document_)
      .then((loaded) => {
        if (cancelled) return;
        setData(loaded);
        setFileName(loaded.fileName);
        setStylePackage(loaded.stylePackages[0]?.id ?? '');
        setLoadError(null);
      })
      .catch(() => {
        if (!cancelled) setLoadError(PACKAGE_LOAD_ERROR);
      });
    return () => {
      cancelled = true;
    };
  }, [document_, loadData, sendRequest]);

  const applyPackage = useCallback((content: StylePackageSettings, documentLanguage: string | null) => {
    setForm(toExportForm(content, { documentLanguage }));
    setExposeSettings(!!content.exposeSettings);
    setInvalidField(null);
  }, []);

  // The selected style package decides every field below it, so it is read whenever it changes - the same
  // request the legacy panel made from its `change` handler.
  useEffect(() => {
    if (!data || !stylePackage) {
      return undefined;
    }
    const sequence = ++latestPackage.current;
    setLoadingPackage(true);
    let cancelled = false;
    loadPackage(sendRequest, stylePackage, document_.scope)
      .then((content) => {
        if (cancelled || sequence !== latestPackage.current) return;
        applyPackage(content, data.documentLanguage);
        setLoadError(null);
        setLoadingPackage(false);
      })
      .catch(() => {
        if (cancelled || sequence !== latestPackage.current) return;
        setLoadError(PACKAGE_LOAD_ERROR);
        setLoadingPackage(false);
      });
    return () => {
      cancelled = true;
    };
  }, [applyPackage, data, document_, loadPackage, sendRequest, stylePackage]);

  const patch = (values: Partial<ExportForm>) => setForm((current) => (current ? { ...current, ...values } : current));

  /** The name to export under: what the user typed, or the server's default, always ending in `.docx`. */
  const exportFileName = (): string => {
    const name = fileName || data?.fileName || '';
    return name && !name.endsWith('.docx') ? `${name}.docx` : name;
  };

  const exportToDocx = async () => {
    setExportError(null);
    setExportWarning(null);
    if (!form) {
      return;
    }
    const name = exportFileName();
    const built = buildExportParams(form, document_, name);
    if ('error' in built) {
      setInvalidField(built.error.field);
      setExportError(built.error.message);
      return;
    }
    setInvalidField(null);

    setExporting(true);
    try {
      const result = await convert(remote, toRequestBody(built.params));
      if (result.warning) {
        setExportWarning(result.warning);
      }
      download(result.blob, name);
    } catch (error) {
      setExportError(withDetail('Error occurred during DOCX generation', messageOf(error)));
    } finally {
      setExporting(false);
    }
  };

  const childOptions = (setting: keyof PanelData['childNames']): SelectOption[] => data?.childNames[setting] ?? [];

  if (loadError && !form) {
    return <div id="style-package-error">{loadError}</div>;
  }

  if (!data || !form) {
    return (
      <div className="panel-loading">
        <span className="sbb-spinner" role="img" aria-label="Loading" />
        <span className="panel-loading-message">{LOADING_MESSAGE}</span>
      </div>
    );
  }

  const exportDisabled = exporting || loadingPackage || data.exportPermission !== 'granted';
  const permissionTitle =
    data.exportPermission === 'denied'
      ? NOT_AUTHORIZED
      : data.exportPermission === 'unknown'
        ? PERMISSION_UNKNOWN
        : undefined;

  return (
    <fieldset className="panel-fieldset" disabled={exporting}>
      <p>Select one of style packages in dropdown below which you wish to use during export.</p>
      <div className="property-wrapper">
        <label htmlFor="style-package-select">Style package:</label>
        <SearchableSelect
          id="style-package-select"
          options={data.stylePackages}
          value={stylePackage}
          onChange={setStylePackage}
          disabled={exporting}
        />
      </div>
      <div id="style-package-error">{loadError}</div>

      {exposeSettings && (
        <div id="style-package-content" className="group-start">
          <p>Selected style package exposes its settings, so you can redefine them.</p>

          <div className="property-wrapper">
            <label htmlFor="template-selector">Template:</label>
            <SearchableSelect
              id="template-selector"
              options={childOptions('templates')}
              value={childValue(childOptions('templates'), form.template)}
              onChange={(value) => patch({ template: value })}
              disabled={exporting}
            />
          </div>

          <div className="property-wrapper">
            <label htmlFor="localization-selector">Localization:</label>
            <SearchableSelect
              id="localization-selector"
              options={childOptions('localization')}
              value={childValue(childOptions('localization'), form.localization)}
              onChange={(value) => patch({ localization: value })}
              disabled={exporting}
            />
          </div>

          {data.webhooksEnabled && (
            <div className="property-wrapper group-start">
              <label htmlFor="webhooks-checkbox">
                <input
                  id="webhooks-checkbox"
                  type="checkbox"
                  checked={form.webhooksEnabled}
                  onChange={(e) => patch({ webhooksEnabled: e.target.checked })}
                />
                Webhooks:
              </label>
              {form.webhooksEnabled && (
                <SearchableSelect
                  id="webhooks-selector"
                  options={childOptions('webhooks')}
                  value={childValue(childOptions('webhooks'), form.webhooks)}
                  onChange={(value) => patch({ webhooks: value })}
                  disabled={exporting}
                />
              )}
            </div>
          )}

          {/* The three page setup rows are a checkbox plus a dropdown, not a dropdown alone: unticked, the
              export carries no value at all and the conversion takes what the reference template says. */}
          <div className="property-wrapper">
            <label htmlFor="orientation">
              <input
                id="orientation"
                type="checkbox"
                checked={form.orientationEnabled}
                onChange={(e) => patch({ orientationEnabled: e.target.checked })}
              />
              Custom orientation
            </label>
            {form.orientationEnabled && (
              <SearchableSelect
                id="orientation-selector"
                options={ORIENTATIONS}
                value={form.orientation}
                onChange={(value) => patch({ orientation: value })}
                disabled={exporting}
              />
            )}
          </div>

          <div className="property-wrapper">
            <label htmlFor="paper-size">
              <input
                id="paper-size"
                type="checkbox"
                checked={form.paperSizeEnabled}
                onChange={(e) => patch({ paperSizeEnabled: e.target.checked })}
              />
              Custom paper size
            </label>
            {form.paperSizeEnabled && (
              <SearchableSelect
                id="paper-size-selector"
                options={PAPER_SIZES}
                value={form.paperSize}
                onChange={(value) => patch({ paperSize: value })}
                disabled={exporting}
              />
            )}
          </div>

          <div className="property-wrapper">
            <label htmlFor="image-density">
              <input
                id="image-density"
                type="checkbox"
                checked={form.imageDensityEnabled}
                onChange={(e) => patch({ imageDensityEnabled: e.target.checked })}
              />
              Custom image density
            </label>
            {form.imageDensityEnabled && (
              <SearchableSelect
                id="image-density-selector"
                options={IMAGE_DENSITIES}
                value={form.imageDensity}
                onChange={(value) => patch({ imageDensity: value })}
                disabled={exporting}
              />
            )}
          </div>

          <div className="property-wrapper">
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

          <div className="property-wrapper">
            <label htmlFor="render-comments">
              <input
                id="render-comments"
                type="checkbox"
                checked={form.renderCommentsEnabled}
                onChange={(e) => patch({ renderCommentsEnabled: e.target.checked })}
              />
              Comments rendering
            </label>
            {form.renderCommentsEnabled && (
              <SearchableSelect
                id="render-comments-selector"
                options={COMMENTS_RENDER_TYPES}
                value={form.renderComments}
                onChange={(value) => patch({ renderComments: value })}
                disabled={exporting}
              />
            )}
          </div>

          {form.renderCommentsEnabled && (
            <div className="property-wrapper" id="render-comments-options" style={{ paddingLeft: 20 }}>
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

          <div className="property-wrapper">
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

          <div className="property-wrapper">
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

          <div className="property-wrapper">
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

          <div className="property-wrapper">
            <label htmlFor="specific-chapters" className="w-chapters">
              <input
                id="specific-chapters"
                type="checkbox"
                checked={form.specificChaptersEnabled}
                onChange={(e) => patch({ specificChaptersEnabled: e.target.checked })}
              />
              Specific higher level chapters
            </label>
            {form.specificChaptersEnabled && (
              <input
                id="chapters"
                className={invalidField === 'chapters' ? 'grows error' : 'grows'}
                type="text"
                placeholder="eg. 1,2,4 etc."
                value={form.specificChapters}
                onChange={(e) => patch({ specificChapters: e.target.value })}
              />
            )}
          </div>

          <div className="property-wrapper">
            <label htmlFor="localization">
              <input
                id="localization"
                type="checkbox"
                checked={form.localizeEnums}
                onChange={(e) => patch({ localizeEnums: e.target.checked })}
              />
              Localize enums
            </label>
            {form.localizeEnums && (
              <SearchableSelect
                id="language"
                options={LANGUAGES}
                value={form.language}
                onChange={(value) => patch({ language: value })}
                disabled={exporting}
              />
            )}
          </div>

          {/* Roles apply only where the project defines any; an empty list hid the whole group before too. */}
          {data.roles.length > 0 && (
            <div className="roles-fields">
              <div className="property-wrapper">
                <label htmlFor="selected-roles">
                  <input
                    id="selected-roles"
                    type="checkbox"
                    checked={form.rolesEnabled}
                    onChange={(e) => patch({ rolesEnabled: e.target.checked })}
                  />
                  Specific Workitem roles
                </label>
              </div>
              {form.rolesEnabled && (
                <div className="property-wrapper" id="roles-wrapper">
                  <SearchableSelect
                    id="roles-selector"
                    multiple
                    options={data.roles}
                    value={form.linkedWorkitemRoles}
                    onChange={(values) => patch({ linkedWorkitemRoles: values })}
                    disabled={exporting}
                  />
                  <SearchableSelect
                    id="roles-direction-selector"
                    options={LINK_ROLE_DIRECTIONS}
                    value={form.linkRoleDirection}
                    onChange={(value) => patch({ linkRoleDirection: value })}
                    disabled={exporting}
                  />
                </div>
              )}
            </div>
          )}

          <div className="property-wrapper">
            <label htmlFor="removal-selector" className="w-auto">
              Removal selector:
            </label>
            <div className="more-info" title={REMOVAL_SELECTOR_HELP} />
            <input
              id="removal-selector"
              className="grows"
              type="text"
              value={form.removalSelector}
              onChange={(e) => patch({ removalSelector: e.target.value })}
            />
          </div>
        </div>
      )}

      <div className="property-wrapper">
        <label htmlFor="filename" className="w-filename">
          File name:
        </label>
        <input id="filename" type="text" value={fileName} onChange={(e) => setFileName(e.target.value)} />
      </div>

      <div className="buttons-wrapper">
        <button type="button" id="export-docx" disabled={exportDisabled} title={permissionTitle} onClick={exportToDocx}>
          <img src={EXPORT_ICON} alt="" />
          Export to DOCX
        </button>
        <span
          id="export-docx-progress"
          className="sbb-spinner"
          role="img"
          aria-label="Loading"
          style={exporting ? { display: 'inline-block' } : undefined}
        />
        <div id="export-error">{exportError}</div>
        <div id="export-warning">{exportWarning}</div>
      </div>
    </fieldset>
  );
}
