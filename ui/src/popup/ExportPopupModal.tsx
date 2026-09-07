import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Modal } from '@sbb-polarion/react-sbb-polarion';
import ToastHost from '../components/ToastHost';
import ExportFormView from '../export/ExportFormView';
import type { PopupData } from '../export/exportData';
import { loadPopupData, loadStylePackage } from '../export/exportData';
import type { ExportForm } from '../export/exportForm';
import { toExportForm } from '../export/exportForm';
import type { ExportField } from '../export/exportParams';
import { buildExportParams, toRequestBody } from '../export/exportParams';
import {
  EXPORT_ERROR,
  EXPORT_SUCCESS,
  clearReports,
  messageOf,
  reportFailure,
  reportRefusal,
  reportSuccess,
  reportWarning,
  withDetail,
} from '../export/reporting';
import { convertDocx, downloadBlob } from '../services/conversion';
import { getCookie, setCookie } from '../services/cookies';
import type { DocumentIdentity } from '../services/exportContext';
import type { StylePackageSettings } from '../services/stylePackage';
import useRemote from '../services/useRemote';
import useDropdownPopupsInDialog from './dialogPortals';

/** The style package the user picked last, offered again next time. The legacy popup's own cookie name. */
const SELECTED_STYLE_PACKAGE_COOKIE = 'selected-style-package';

const LOAD_ERROR = 'Error occurred loading form data';
const PACKAGE_LOAD_ERROR = 'Error occurred loading style package data';

/** Every element id of this form is prefixed with it, which is what the legacy popup's markup used. */
const IDS = 'popup-';

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

/**
 * The "Export to DOCX" dialog: the React port of `ExportPopup.js` + `popupForm.html`.
 *
 * Opened from the document editor toolbar, which is the extension's only entry point for it - every DOCX
 * export is a Live Document, so there is no document type to switch rows by and no bulk export to collect
 * parameters for. It takes what it is exporting as a prop rather than reading it itself, so the dev harness
 * and the tests can point it at a document without a page URL.
 *
 * The chrome is RSP's shared `Modal` (a native `<dialog>`: the top layer, the backdrop and Escape for free),
 * so the micromodal library and stylesheet the legacy popup needed on the page are gone. The form inside it
 * is `export/ExportFormView.tsx`, which the document properties side panel renders as well; what is left
 * here is the dialog's own business - reading the data, remembering the style package in a cookie and
 * running the conversion.
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

  /** Why the form cannot be used at all. Everything else it has to say is a toast - see `reporting.ts`. */
  const [loadError, setLoadError] = useState<string | null>(null);

  /** Which package load is the current one; a slower earlier one must not overwrite it. */
  const latestPackage = useRef(0);

  /** The form, which is what locates the dialog around it - see {@link useDropdownPopupsInDialog}. */
  const form_ = useRef<HTMLDivElement>(null);
  useDropdownPopupsInDialog(form_);

  const busy = progress !== null;

  /** What the last operation said, taken back before the next one starts. */
  const clearAlerts = useCallback(() => {
    clearReports();
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
        setLoadError(withDetail(LOAD_ERROR, messageOf(failure)));
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
        setLoadError(null);
        setProgress(null);
      })
      .catch((failure: unknown) => {
        if (cancelled || sequence !== latestPackage.current) return;
        setLoadError(withDetail(PACKAGE_LOAD_ERROR, messageOf(failure)));
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
      reportRefusal(built.error.message);
      return;
    }
    setInvalidField(null);

    setProgress('Generating DOCX');
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
      setProgress(null);
    }
  };

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
      {/* Inside the dialog on purpose: it is a native `<dialog>` in the top layer, and a toast host outside
          it would be painted behind the dialog and dimmed by its backdrop. */}
      <ToastHost />

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
        busy={busy}
        loadError={loadError}
        formRef={form_}
        overlay={
          busy && (
            <div className="in-progress-overlay show">
              <span className="sbb-spinner" role="img" aria-label="Loading" />
              <span id="in-progress-message">{progress}</span>
            </div>
          )
        }
      />
    </Modal>
  );
}
