import type { ReactNode, RefObject } from 'react';
import { SearchableSelect } from '@sbb-polarion/react-sbb-polarion';
import type { SelectOption } from '@sbb-polarion/react-sbb-polarion';
import type { ChildNames } from '../services/stylePackage';
import StylePackageSettingsView from './StylePackageSettingsView';
import type { ExportForm } from './exportForm';
import type { ExportField } from './exportParams';
import { FieldCell, FieldRow } from './formRows';

/** The option lists an export form offers, whoever read them. Both `PopupData` and `PanelData` are one. */
export interface ExportFormData {
  stylePackages: SelectOption[];
  childNames: ChildNames;
  /** Empty where the project defines no link roles, in which case the roles row is not offered at all. */
  roles: SelectOption[];
  webhooksEnabled: boolean;
}

export interface ExportFormViewProps {
  /**
   * What every element id in the form is prefixed with (`popup-` in the dialog, nothing in the panel).
   *
   * The two surfaces have always had ids of their own, and the visual references and the suites address
   * them by those. Nothing depends on them being different - each form is alone in its shadow root - so
   * this is the one thing the shared markup is parameterized by rather than unified.
   */
  ids: string;
  /** Null until the option lists have been read; the form then shows what it can and no dropdown options. */
  data: ExportFormData | null;
  stylePackage: string;
  onStylePackage: (name: string) => void;
  /** Null until a style package has been read into it. */
  form: ExportForm | null;
  onPatch: (values: Partial<ExportForm>) => void;
  /** Whether the selected style package invites the user to redefine its settings. */
  exposeSettings: boolean;
  fileName: string;
  onFileName: (fileName: string) => void;
  /** The field an export was refused on, which is then marked. */
  invalidField: ExportField | null;
  /** Something is running: every control is out of reach until it is done. */
  busy: boolean;
  /**
   * Why the form cannot be used, where that is the case: the option lists or the style package behind it
   * could not be read.
   *
   * The one message that stays in the form. Everything else an export surface has to say is an event and is
   * reported as a toast (see `reporting.ts`); this is a state, and a toast that came and went would leave a
   * form that quietly does not work.
   */
  loadError: string | null;
  /** The surface's own action area: the panel's "Export to DOCX" button. The dialog's are its footer. */
  actions?: ReactNode;
  /** Covers the form while the surface is busy: the dialog's in-progress overlay. */
  overlay?: ReactNode;
  /** The form element, which is what locates the dialog around it - see popup/dialogPortals.ts. */
  formRef?: RefObject<HTMLDivElement | null>;
}

/**
 * The export form: everything the "Export to DOCX" dialog and the Document Properties side panel have in
 * common, which is all of it but the chrome.
 *
 * The two used to be two copies of the same form - the same rows, the same style package, the same request -
 * laid out differently by hand: the dialog in two fixed flex columns, the panel in one, each with its own
 * label widths, its own way of hiding an optional field and its own way of reporting a failure. They render
 * this now, and differ only in what surrounds it: RSP's `Modal` with its footer, or a `<fieldset>` in the
 * properties pane with an "Export to DOCX" button of its own (see {@link ExportFormViewProps.actions}).
 *
 * The layout is one row model against one set of columns, and it follows the room the form has rather than
 * which surface it is: a section is one column in a 360px pane and two in a 700px dialog, decided by a
 * container query on this element. See `export-form.css`.
 */
export default function ExportFormView({
  ids,
  data,
  stylePackage,
  onStylePackage,
  form,
  onPatch,
  exposeSettings,
  fileName,
  onFileName,
  invalidField,
  busy,
  loadError,
  actions,
  overlay,
  formRef,
}: Readonly<ExportFormViewProps>) {
  const id = (name: string): string => `${ids}${name}`;
  const settingsShown = !!form && exposeSettings;

  return (
    <div className="docx-export-form" ref={formRef}>
      {overlay}

      <p>Select one of style packages in dropdown below which you wish to use during export.</p>
      <FieldRow label="Style package:" labelFor={id('style-package-select')}>
        <FieldCell>
          <SearchableSelect
            id={id('style-package-select')}
            options={data?.stylePackages ?? []}
            value={stylePackage}
            onChange={onStylePackage}
            disabled={busy}
          />
        </FieldCell>
      </FieldRow>

      {settingsShown && form && (
        <div id={id('style-package-content')} className="settings-block group-start">
          <p>Selected style package exposes its settings, so you can redefine them.</p>

          <StylePackageSettingsView
            ids={ids}
            form={form}
            onPatch={onPatch}
            childNames={data?.childNames ?? null}
            roles={data?.roles ?? []}
            webhooksEnabled={!!data?.webhooksEnabled}
            busy={busy}
            invalidField={invalidField}
          />
        </div>
      )}

      <div className="docx-section group-start">
        <FieldRow className="full-row" rowId={id('filename-wrapper')} label="File name:" labelFor={id('filename')}>
          <FieldCell grows>
            <input
              id={id('filename')}
              type="text"
              value={fileName}
              onChange={(event) => onFileName(event.target.value)}
            />
          </FieldCell>
        </FieldRow>
      </div>

      {/* Only where there is something to say: an empty block would keep its padding above the buttons. */}
      {loadError && (
        <div className="notifications">
          <div id={id('load-error')} className="alert alert-error">
            {loadError}
          </div>
        </div>
      )}

      {actions}
    </div>
  );
}
