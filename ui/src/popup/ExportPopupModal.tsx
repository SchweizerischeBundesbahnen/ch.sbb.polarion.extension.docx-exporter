import { type CSSProperties, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Modal, SearchableSelect } from '@sbb-polarion/react-sbb-polarion';
import type { SelectOption } from '@sbb-polarion/react-sbb-polarion';
import type { PopupData } from '../export/exportData';
import { loadPopupData, loadStylePackage } from '../export/exportData';
import type { ExportForm } from '../export/exportForm';
import { childValue, toExportForm } from '../export/exportForm';
import type { ExportField } from '../export/exportParams';
import { buildExportParams, toRequestBody } from '../export/exportParams';
import { convertDocx, downloadBlob } from '../services/conversion';
import { getCookie, setCookie } from '../services/cookies';
import type { DocumentIdentity } from '../services/exportContext';
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
import useDropdownPopupsInDialog from './dialogPortals';

/** The style package the user picked last, offered again next time. The legacy popup's own cookie name. */
const SELECTED_STYLE_PACKAGE_COOKIE = 'selected-style-package';

const LOAD_ERROR = 'Error occurred loading form data';
const PACKAGE_LOAD_ERROR = 'Error occurred loading style package data';
const EXPORT_ERROR = 'Error occurred during DOCX generation';

/** What the dialog reaches outside itself for, so the dev harness and the tests can replace it. */
export interface ExportPopupDependencies {
  loadData?: typeof loadPopupData;
  loadPackage?: typeof loadStylePackage;
  convert?: typeof convertDocx;
  download?: typeof downloadBlob;
}

export interface ExportPopupModalProps {
  /**
   * Where the document being exported lives, as the page URL says. Must be stable across renders - it is
   * what the dialog reads its data for, so a fresh object each render would restart that read.
   */
  document: DocumentIdentity;
  onClose: () => void;
  deps?: ExportPopupDependencies;
}

/** `<prefix>: <detail>`, or the prefix alone - the legacy `prefix + ": " + message`. */
const withDetail = (prefix: string, detail: string): string => (detail ? `${prefix}: ${detail}` : prefix);

/** What a rejected read or conversion says, which is the server's message or nothing. */
const messageOf = (error: unknown): string => (error instanceof Error ? error.message : '');

/**
 * Reserves a control's space while hiding it, which is how the legacy popup hid the value field of a row
 * whose switch is off: `visibility` rather than `display`, so ticking a checkbox does not reflow the column
 * around it. The three page setup dropdowns are the exception - see the rows themselves.
 */
const reserved = (visible: boolean): CSSProperties | undefined => (visible ? undefined : { visibility: 'hidden' });

/**
 * The "Export to DOCX" dialog: the React port of `ExportPopup.js` + `popupForm.html`.
 *
 * Opened from the document editor toolbar, which is the extension's only entry point for it - every DOCX
 * export is a Live Document, so there is no document type to switch rows by and no bulk export to collect
 * parameters for. It takes what it is exporting as a prop rather than reading it itself, so the dev harness
 * and the tests can point it at a document without a page URL.
 *
 * The chrome is RSP's shared `Modal` (a native `<dialog>`: the top layer, the backdrop and Escape for free),
 * so the micromodal library and stylesheet the legacy popup needed on the page are gone. What is this
 * extension's own is the form body, whose two-column layout comes from `export-popup.css` - injected into
 * the same shadow root the dialog is mounted in, see `mount.tsx`.
 *
 * The same form as the document properties side panel, laid out in two columns instead of one, and reading
 * the same `export/` model: what a style package means for the controls is `exportForm.ts` and what the
 * export then sends is `exportParams.ts`, so the two surfaces cannot drift apart.
 */
export default function ExportPopupModal({ document: document_, onClose, deps }: Readonly<ExportPopupModalProps>) {
  const { sendRequest, sendAbsoluteRequest } = useRemote();
  const loadData = deps?.loadData ?? loadPopupData;
  const loadPackage = deps?.loadPackage ?? loadStylePackage;
  const convert = deps?.convert ?? convertDocx;
  const download = deps?.download ?? downloadBlob;

  const remote = useMemo(() => ({ sendRequest, sendAbsoluteRequest }), [sendRequest, sendAbsoluteRequest]);

  const [data, setData] = useState<PopupData | null>(null);
  const [stylePackage, setStylePackage] = useState('');
  const [exposeSettings, setExposeSettings] = useState(false);
  const [form, setForm] = useState<ExportForm | null>(null);
  const [fileName, setFileName] = useState('');
  const [invalidField, setInvalidField] = useState<ExportField | null>(null);

  /** What the form is busy with, or null. One overlay for all three operations, as the legacy popup had. */
  const [progress, setProgress] = useState<string | null>('Loading form data');

  const [warning, setWarning] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  /** Which package load is the current one; a slower earlier one must not overwrite it. */
  const latestPackage = useRef(0);

  /** The form, which is what locates the dialog around it - see {@link useDropdownPopupsInDialog}. */
  const form_ = useRef<HTMLDivElement>(null);
  useDropdownPopupsInDialog(form_);

  const busy = progress !== null;

  const clearAlerts = useCallback(() => {
    setWarning(null);
    setError(null);
    setSuccess(null);
    setInvalidField(null);
  }, []);

  // Everything the popup offers, read once. Any failure among these reads leaves the form unusable and says
  // so, which is what the legacy popup did: it showed this one message and never enabled its Export button.
  useEffect(() => {
    let cancelled = false;
    loadData(sendRequest, document_)
      .then((loaded) => {
        if (cancelled) return;
        setData(loaded);
        setFileName(loaded.fileName);
        // The package used last is offered again, as long as this document still allows it.
        const remembered = getCookie(SELECTED_STYLE_PACKAGE_COOKIE);
        const preselected = loaded.stylePackages.some((option) => option.id === remembered)
          ? (remembered as string)
          : (loaded.stylePackages[0]?.id ?? '');
        setStylePackage(preselected);
      })
      .catch((failure: unknown) => {
        if (cancelled) return;
        setError(withDetail(LOAD_ERROR, messageOf(failure)));
        setProgress(null);
      });
    return () => {
      cancelled = true;
    };
  }, [document_, loadData, sendRequest]);

  // The selected style package decides every field below it, so it is read whenever it changes - the same
  // request the legacy popup made from its `change` handler.
  useEffect(() => {
    if (!data || !stylePackage) {
      return undefined;
    }
    setCookie(SELECTED_STYLE_PACKAGE_COOKIE, stylePackage);
    const sequence = ++latestPackage.current;
    setProgress('Loading style package data');
    let cancelled = false;
    loadPackage(sendRequest, stylePackage, document_.scope)
      .then((content: StylePackageSettings) => {
        if (cancelled || sequence !== latestPackage.current) return;
        setForm(toExportForm(content, { documentLanguage: data.documentLanguage }));
        setExposeSettings(!!content.exposeSettings);
        setInvalidField(null);
        setProgress(null);
      })
      .catch((failure: unknown) => {
        if (cancelled || sequence !== latestPackage.current) return;
        setError(withDetail(PACKAGE_LOAD_ERROR, messageOf(failure)));
        setProgress(null);
      });
    return () => {
      cancelled = true;
    };
  }, [data, document_.scope, loadPackage, sendRequest, stylePackage]);

  const patch = (values: Partial<ExportForm>) => setForm((current) => (current ? { ...current, ...values } : current));

  /** The name to export under: what the user typed, or the server's default, always ending in `.docx`. */
  const exportFileName = (): string => {
    const name = fileName || data?.fileName || '';
    return name && !name.endsWith('.docx') ? `${name}.docx` : name;
  };

  const exportToDocx = async () => {
    clearAlerts();
    if (!form) {
      return;
    }
    const name = exportFileName();
    const built = buildExportParams(form, document_, name);
    if ('error' in built) {
      setInvalidField(built.error.field);
      setError(built.error.message);
      return;
    }
    setInvalidField(null);

    setProgress('Generating DOCX');
    try {
      const result = await convert(remote, toRequestBody(built.params));
      if (result.warning) {
        setWarning(result.warning);
      }
      download(result.blob, name);
      setSuccess('DOCX was successfully generated');
    } catch (failure) {
      setError(withDetail(EXPORT_ERROR, messageOf(failure)));
    } finally {
      setProgress(null);
    }
  };

  const childOptions = (setting: keyof PopupData['childNames']): SelectOption[] => data?.childNames[setting] ?? [];

  return (
    <Modal
      open
      title="Export to DOCX"
      okText="Export"
      cancelText="Close"
      okDisabled={busy || !form}
      onOk={() => void exportToDocx()}
      onCancel={onClose}
    >
      <div className="form-wrapper docx-export-form" ref={form_}>
        {busy && (
          <div className="in-progress-overlay show">
            <span className="sbb-spinner" role="img" aria-label="Loading" />
            <span id="in-progress-message">{progress}</span>
          </div>
        )}

        {/* Only where there is something to say. The legacy markup carried this block with three hidden
            alerts inside it, so its 10px of padding sat above the form whether or not anything was shown. */}
        {(warning || error || success) && (
          <div className="notifications">
            {warning && <div className="alert alert-warning">{warning}</div>}
            {error && <div className="alert alert-error">{error}</div>}
            {success && <div className="alert alert-success">{success}</div>}
          </div>
        )}

        <div id="popup-style-package" className="flex-container">
          <p>Select one of style packages in dropdown below which you wish to use during export.</p>
          <div className="flex-column">
            <div className="property-wrapper">
              <label htmlFor="popup-style-package-select" className="fixed-width w-1">
                Style package:
              </label>
              <SearchableSelect
                id="popup-style-package-select"
                options={data?.stylePackages ?? []}
                value={stylePackage}
                onChange={setStylePackage}
                disabled={busy}
              />
            </div>
          </div>
        </div>

        {form && exposeSettings && (
          <div id="popup-style-package-content" className="group-start">
            <p>Selected style package exposes its settings, so you can redefine them.</p>

            <div className="flex-container">
              <div className="flex-column">
                <div className="property-wrapper">
                  <label htmlFor="popup-template-selector" className="fixed-width w-1">
                    Template:
                  </label>
                  <SearchableSelect
                    id="popup-template-selector"
                    options={childOptions('templates')}
                    value={childValue(childOptions('templates'), form.template)}
                    onChange={(value) => patch({ template: value })}
                    disabled={busy}
                  />
                </div>
                <div className="property-wrapper">
                  <label htmlFor="popup-localization-selector" className="fixed-width w-1">
                    Localization:
                  </label>
                  <SearchableSelect
                    id="popup-localization-selector"
                    options={childOptions('localization')}
                    value={childValue(childOptions('localization'), form.localization)}
                    onChange={(value) => patch({ localization: value })}
                    disabled={busy}
                  />
                </div>
              </div>
            </div>

            {data?.webhooksEnabled && (
              <div className="flex-container group-start" id="popup-webhooks-container">
                <div className="flex-column">
                  <div className="property-wrapper">
                    <label htmlFor="popup-webhooks-checkbox" className="fixed-width w-1">
                      <input
                        id="popup-webhooks-checkbox"
                        type="checkbox"
                        checked={form.webhooksEnabled}
                        onChange={(e) => patch({ webhooksEnabled: e.target.checked })}
                      />
                      Webhooks:
                    </label>
                    <div style={reserved(form.webhooksEnabled)}>
                      <SearchableSelect
                        id="popup-webhooks-selector"
                        options={childOptions('webhooks')}
                        value={childValue(childOptions('webhooks'), form.webhooks)}
                        onChange={(value) => patch({ webhooks: value })}
                        disabled={busy}
                      />
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/*
              One container for the whole settings block, NOT one per group of rows. A flex row is as tall
              as its taller column, so a column split across several containers cannot use the space a
              shorter neighbor leaves: the legacy popup nested a container per page setup row here, and each
              of those rows therefore held a line of its own across both columns. Two continuous columns
              have neither problem. Keep them roughly equal in row count when adding a field.
            */}
            <div className="flex-container" id="popup-settings-columns">
              <div className="flex-column">
                {/* The three page setup rows are a checkbox plus a dropdown, not a dropdown alone: unticked,
                    the export carries no value at all and the conversion takes what the reference template
                    says. The dropdown is removed rather than merely hidden, as the legacy row was - it
                    switched `display`, where every other optional field here switched `visibility`. */}
                <div className="property-wrapper">
                  <label htmlFor="popup-orientation">
                    <input
                      id="popup-orientation"
                      type="checkbox"
                      checked={form.orientationEnabled}
                      onChange={(e) => patch({ orientationEnabled: e.target.checked })}
                    />
                    Custom orientation
                  </label>
                  {form.orientationEnabled && (
                    <SearchableSelect
                      id="popup-orientation-selector"
                      options={ORIENTATIONS}
                      value={form.orientation}
                      onChange={(value) => patch({ orientation: value })}
                      disabled={busy}
                    />
                  )}
                </div>
                <div className="property-wrapper">
                  <label htmlFor="popup-render-comments">
                    <input
                      id="popup-render-comments"
                      type="checkbox"
                      checked={form.renderCommentsEnabled}
                      onChange={(e) => patch({ renderCommentsEnabled: e.target.checked })}
                    />
                    Comments rendering
                  </label>
                  <div style={reserved(form.renderCommentsEnabled)}>
                    <SearchableSelect
                      id="popup-render-comments-selector"
                      options={COMMENTS_RENDER_TYPES}
                      value={form.renderComments}
                      onChange={(value) => patch({ renderComments: value })}
                      disabled={busy}
                    />
                  </div>
                </div>
                {form.renderCommentsEnabled && (
                  <div className="property-wrapper" id="popup-render-comments-options">
                    <label htmlFor="popup-include-unreferenced-comments" title={UNREFERENCED_COMMENTS_HELP}>
                      <input
                        id="popup-include-unreferenced-comments"
                        type="checkbox"
                        checked={form.includeUnreferencedComments}
                        onChange={(e) => patch({ includeUnreferencedComments: e.target.checked })}
                      />
                      include unreferenced
                    </label>
                  </div>
                )}
                <div className="property-wrapper">
                  <label htmlFor="popup-cut-empty-chapters">
                    <input
                      id="popup-cut-empty-chapters"
                      type="checkbox"
                      checked={form.cutEmptyChapters}
                      onChange={(e) => patch({ cutEmptyChapters: e.target.checked })}
                    />
                    Cut empty chapters (any level)
                  </label>
                </div>
                <div className="property-wrapper">
                  <label htmlFor="popup-cut-empty-wi-attributes">
                    <input
                      id="popup-cut-empty-wi-attributes"
                      type="checkbox"
                      checked={form.cutEmptyWorkitemAttributes}
                      onChange={(e) => patch({ cutEmptyWorkitemAttributes: e.target.checked })}
                    />
                    Cut empty Workitem attributes
                  </label>
                </div>
                <div className="property-wrapper">
                  <label htmlFor="popup-cut-urls">
                    <input
                      id="popup-cut-urls"
                      type="checkbox"
                      checked={form.cutLocalURLs}
                      onChange={(e) => patch({ cutLocalURLs: e.target.checked })}
                    />
                    Cut local Polarion URLs
                  </label>
                </div>
                <div className="property-wrapper">
                  <label htmlFor="popup-preserve-table-styles">
                    <input
                      id="popup-preserve-table-styles"
                      type="checkbox"
                      checked={form.preserveTableStyles}
                      onChange={(e) => patch({ preserveTableStyles: e.target.checked })}
                    />
                    Preserve table styles
                  </label>
                </div>
              </div>
              <div className="flex-column">
                <div className="property-wrapper">
                  <label htmlFor="popup-paper-size">
                    <input
                      id="popup-paper-size"
                      type="checkbox"
                      checked={form.paperSizeEnabled}
                      onChange={(e) => patch({ paperSizeEnabled: e.target.checked })}
                    />
                    Custom paper size
                  </label>
                  {form.paperSizeEnabled && (
                    <SearchableSelect
                      id="popup-paper-size-selector"
                      options={PAPER_SIZES}
                      value={form.paperSize}
                      onChange={(value) => patch({ paperSize: value })}
                      disabled={busy}
                    />
                  )}
                </div>
                <div className="property-wrapper">
                  <label htmlFor="popup-image-density">
                    <input
                      id="popup-image-density"
                      type="checkbox"
                      checked={form.imageDensityEnabled}
                      onChange={(e) => patch({ imageDensityEnabled: e.target.checked })}
                    />
                    Custom image density
                  </label>
                  {form.imageDensityEnabled && (
                    <SearchableSelect
                      id="popup-image-density-selector"
                      options={IMAGE_DENSITIES}
                      value={form.imageDensity}
                      onChange={(value) => patch({ imageDensity: value })}
                      disabled={busy}
                    />
                  )}
                </div>
                <div className="property-wrapper">
                  <label htmlFor="popup-specific-chapters">
                    <input
                      id="popup-specific-chapters"
                      type="checkbox"
                      checked={form.specificChaptersEnabled}
                      onChange={(e) => patch({ specificChaptersEnabled: e.target.checked })}
                    />
                    Specific higher level chapters
                  </label>
                  <input
                    id="popup-chapters"
                    className={invalidField === 'chapters' ? 'grows error' : 'grows'}
                    type="text"
                    placeholder="eg. 1,2,4 etc."
                    style={reserved(form.specificChaptersEnabled)}
                    value={form.specificChapters}
                    onChange={(e) => patch({ specificChapters: e.target.value })}
                  />
                </div>
                <div className="property-wrapper">
                  <label htmlFor="popup-localization">
                    <input
                      id="popup-localization"
                      type="checkbox"
                      checked={form.localizeEnums}
                      onChange={(e) => patch({ localizeEnums: e.target.checked })}
                    />
                    Localize enums
                  </label>
                  <div style={reserved(form.localizeEnums)}>
                    <SearchableSelect
                      id="popup-language"
                      options={LANGUAGES}
                      value={form.language}
                      onChange={(value) => patch({ language: value })}
                      disabled={busy}
                    />
                  </div>
                </div>
                {/* Roles apply only where the project defines any; an empty list hid the whole group before
                    too - the legacy popup rendered an empty multiselect, which offered nothing to pick. */}
                {data && data.roles.length > 0 && (
                  <div className="property-wrapper" id="popup-roles-wrapper">
                    <label htmlFor="popup-selected-roles">
                      <input
                        id="popup-selected-roles"
                        type="checkbox"
                        checked={form.rolesEnabled}
                        onChange={(e) => patch({ rolesEnabled: e.target.checked })}
                      />
                      Specific Workitem roles
                    </label>
                    {form.rolesEnabled && (
                      <>
                        <SearchableSelect
                          id="popup-roles-selector"
                          multiple
                          options={data.roles}
                          value={form.linkedWorkitemRoles}
                          onChange={(values) => patch({ linkedWorkitemRoles: values })}
                          disabled={busy}
                        />
                        <SearchableSelect
                          id="popup-roles-direction-selector"
                          options={LINK_ROLE_DIRECTIONS}
                          value={form.linkRoleDirection}
                          onChange={(value) => patch({ linkRoleDirection: value })}
                          disabled={busy}
                        />
                      </>
                    )}
                  </div>
                )}
              </div>
            </div>

            <div className="property-wrapper">
              <label htmlFor="popup-removal-selector">Removal selector:</label>
              <div className="more-info" title={REMOVAL_SELECTOR_HELP} />
              <input
                id="popup-removal-selector"
                className="grows"
                type="text"
                value={form.removalSelector}
                onChange={(e) => patch({ removalSelector: e.target.value })}
              />
            </div>
          </div>
        )}

        <div className="property-wrapper" id="popup-filename-wrapper">
          <label htmlFor="popup-filename">File name:</label>
          <input
            id="popup-filename"
            className="grows"
            type="text"
            value={fileName}
            onChange={(e) => setFileName(e.target.value)}
          />
        </div>
      </div>
    </Modal>
  );
}
