import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import ToastHost from '../components/ToastHost';
import ExportFormView from '../export/ExportFormView';
import type { PanelData } from '../export/exportData';
import { loadPanelData, loadStylePackage } from '../export/exportData';
import type { ExportForm } from '../export/exportForm';
import { toExportForm } from '../export/exportForm';
import type { ExportField } from '../export/exportParams';
import { buildExportParams, toRequestBody } from '../export/exportParams';
import {
  EXPORT_ERROR,
  EXPORT_SUCCESS,
  clearReports,
  reportFailure,
  reportRefusal,
  reportSuccess,
  reportWarning,
} from '../export/reporting';
import { convertDocx, downloadBlob } from '../services/conversion';
import type { DocumentIdentity } from '../services/exportContext';
import { currentDocumentLocation, toDocumentIdentity } from '../services/exportContext';
import type { StylePackageSettings } from '../services/stylePackage';
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

/** The panel's element ids are the legacy fragment's own, which carry no prefix. */
const IDS = '';

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

/**
 * DOCX Exporter's Document Properties side panel: the React port of `sidePanelContent.html` +
 * `ExportPanel.js`.
 *
 * It is mounted by `mountSidePanel` into a shadow root on the fragment div Polarion injects into the
 * document editor's Document Properties pane. The form itself is `export/ExportFormView.tsx`, which the
 * "Export to DOCX" dialog renders as well - the two used to be two copies of the same form; what is left
 * here is the panel's own business: reading the data, running the conversion, and the "Export to DOCX"
 * button, which is the panel's own and not the dialog's footer.
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
    // What the last operation said, taken back before this one starts
    clearReports();
    if (!form) {
      return;
    }
    const name = exportFileName();
    const built = buildExportParams(form, document_, name);
    if ('error' in built) {
      setInvalidField(built.error.field);
      reportRefusal(built.error.message);
      return;
    }
    setInvalidField(null);

    setExporting(true);
    try {
      const result = await convert(remote, toRequestBody(built.params));
      if (result.warning) {
        reportWarning(result.warning);
      }
      download(result.blob, name);
      reportSuccess(EXPORT_SUCCESS);
    } catch (failure) {
      reportFailure(EXPORT_ERROR, failure);
    } finally {
      setExporting(false);
    }
  };

  // Nothing to show a form for: the option lists the panel offers could not be read at all. Reported as the
  // same alert the form itself would carry, which is the one the export dialog shows in the same case.
  if (loadError && !form) {
    return (
      <div className="notifications">
        <div id="load-error" className="alert alert-error">
          {loadError}
        </div>
      </div>
    );
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
    <>
      {/* Outside the fieldset on purpose: it is disabled while an export runs, and a disabled fieldset
          disables every control inside it - a toast's own close button included. `surface`, because the
          dialog reports over this panel whenever one is open, whichever of the two mounted first. */}
      <ToastHost surface="panel" />

      <fieldset className="panel-fieldset" disabled={exporting}>
        <ExportFormView
          ids={IDS}
          data={data}
          stylePackage={stylePackage}
          onStylePackage={setStylePackage}
          form={form}
          onPatch={patch}
          exposeSettings={exposeSettings}
          fileName={fileName}
          onFileName={setFileName}
          invalidField={invalidField}
          busy={exporting}
          loadError={loadError}
          actions={
            <div className="buttons-wrapper">
              <button
                type="button"
                id="export-docx"
                disabled={exportDisabled}
                title={permissionTitle}
                onClick={() => void exportToDocx()}
              >
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
            </div>
          }
        />
      </fieldset>
    </>
  );
}
