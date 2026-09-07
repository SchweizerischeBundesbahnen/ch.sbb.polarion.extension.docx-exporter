import type { ReactNode, RefObject } from 'react';
import { SearchableSelect } from '@sbb-polarion/react-sbb-polarion';
import type { SelectOption } from '@sbb-polarion/react-sbb-polarion';
import {
  COMMENTS_RENDER_TYPES,
  type ChildNames,
  type ChildSetting,
  IMAGE_DENSITIES,
  LANGUAGES,
  LINK_ROLE_DIRECTIONS,
  ORIENTATIONS,
  PAPER_SIZES,
  REMOVAL_SELECTOR_HELP,
  UNREFERENCED_COMMENTS_HELP,
} from '../services/stylePackage';
import type { ExportForm } from './exportForm';
import { childValue } from './exportForm';
import type { ExportField } from './exportParams';
import { FieldCell, FieldRow, SwitchRow } from './formRows';

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
  const childOptions = (setting: ChildSetting): SelectOption[] => data?.childNames[setting] ?? [];

  const settingsShown = !!form && exposeSettings;
  const rolesShown = !!data && data.roles.length > 0;

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

          {/* The named configurations the package points at. */}
          <div className="docx-section">
            <FieldRow label="Template:" labelFor={id('template-selector')}>
              <FieldCell>
                <SearchableSelect
                  id={id('template-selector')}
                  options={childOptions('templates')}
                  value={childValue(childOptions('templates'), form.template)}
                  onChange={(value) => onPatch({ template: value })}
                  disabled={busy}
                />
              </FieldCell>
            </FieldRow>
            <FieldRow label="Localization:" labelFor={id('localization-selector')}>
              <FieldCell>
                <SearchableSelect
                  id={id('localization-selector')}
                  options={childOptions('localization')}
                  value={childValue(childOptions('localization'), form.localization)}
                  onChange={(value) => onPatch({ localization: value })}
                  disabled={busy}
                />
              </FieldCell>
            </FieldRow>
            {data?.webhooksEnabled && (
              <SwitchRow
                id={id('webhooks-checkbox')}
                label="Webhooks:"
                checked={form.webhooksEnabled}
                onChange={(checked) => onPatch({ webhooksEnabled: checked })}
              >
                <FieldCell shown={form.webhooksEnabled}>
                  <SearchableSelect
                    id={id('webhooks-selector')}
                    options={childOptions('webhooks')}
                    value={childValue(childOptions('webhooks'), form.webhooks)}
                    onChange={(value) => onPatch({ webhooks: value })}
                    disabled={busy}
                  />
                </FieldCell>
              </SwitchRow>
            )}
          </div>

          {/* The page the document is laid out on.

              Each of the three is a checkbox plus a dropdown, not a dropdown alone: unticked, the export
              carries no value at all and the conversion takes what the reference template says. That is
              also why the dropdown is removed rather than merely hidden here, where every other optional
              value in this form keeps its space - an absent dropdown is the state being described. */}
          <div className="docx-section group-start">
            <SwitchRow
              id={id('paper-size')}
              label="Custom paper size"
              checked={form.paperSizeEnabled}
              onChange={(checked) => onPatch({ paperSizeEnabled: checked })}
            >
              {form.paperSizeEnabled && (
                <FieldCell>
                  <SearchableSelect
                    id={id('paper-size-selector')}
                    options={PAPER_SIZES}
                    value={form.paperSize}
                    onChange={(value) => onPatch({ paperSize: value })}
                    disabled={busy}
                  />
                </FieldCell>
              )}
            </SwitchRow>
            <SwitchRow
              id={id('orientation')}
              label="Custom orientation"
              checked={form.orientationEnabled}
              onChange={(checked) => onPatch({ orientationEnabled: checked })}
            >
              {form.orientationEnabled && (
                <FieldCell>
                  <SearchableSelect
                    id={id('orientation-selector')}
                    options={ORIENTATIONS}
                    value={form.orientation}
                    onChange={(value) => onPatch({ orientation: value })}
                    disabled={busy}
                  />
                </FieldCell>
              )}
            </SwitchRow>
            <SwitchRow
              id={id('image-density')}
              label="Custom image density"
              checked={form.imageDensityEnabled}
              onChange={(checked) => onPatch({ imageDensityEnabled: checked })}
            >
              {form.imageDensityEnabled && (
                <FieldCell>
                  <SearchableSelect
                    id={id('image-density-selector')}
                    options={IMAGE_DENSITIES}
                    value={form.imageDensity}
                    onChange={(value) => onPatch({ imageDensity: value })}
                    disabled={busy}
                  />
                </FieldCell>
              )}
            </SwitchRow>
          </div>

          {/* What the conversion carries over and what it leaves behind.

              The order is the one the two-column layout wants, and the rows flow across before they flow
              down: what reads as a list in a properties pane is two columns in a dialog, with the pair that
              belongs together ("cut empty chapters" / "cut empty Workitem attributes") side by side. */}
          <div className="docx-section group-start">
            <SwitchRow
              id={id('preserve-table-styles')}
              label="Preserve table styles"
              checked={form.preserveTableStyles}
              onChange={(checked) => onPatch({ preserveTableStyles: checked })}
            />
            <SwitchRow
              id={id('cut-urls')}
              label="Cut local Polarion URLs"
              checked={form.cutLocalURLs}
              onChange={(checked) => onPatch({ cutLocalURLs: checked })}
            />
            <SwitchRow
              id={id('cut-empty-chapters')}
              label="Cut empty chapters (any level)"
              checked={form.cutEmptyChapters}
              onChange={(checked) => onPatch({ cutEmptyChapters: checked })}
            />
            <SwitchRow
              id={id('cut-empty-wi-attributes')}
              label="Cut empty Workitem attributes"
              checked={form.cutEmptyWorkitemAttributes}
              onChange={(checked) => onPatch({ cutEmptyWorkitemAttributes: checked })}
            />
            <SwitchRow
              id={id('localization')}
              label="Localize enums"
              checked={form.localizeEnums}
              onChange={(checked) => onPatch({ localizeEnums: checked })}
            >
              <FieldCell shown={form.localizeEnums}>
                <SearchableSelect
                  id={id('language')}
                  options={LANGUAGES}
                  value={form.language}
                  onChange={(value) => onPatch({ language: value })}
                  disabled={busy}
                />
              </FieldCell>
            </SwitchRow>
          </div>

          {/* How comments are rendered, and the option that goes with it - a section of its own, since that
              option belongs under the row rather than beside it. */}
          <div className="docx-section group-start">
            <SwitchRow
              className="full-row"
              id={id('render-comments')}
              label="Comments rendering"
              checked={form.renderCommentsEnabled}
              onChange={(checked) => onPatch({ renderCommentsEnabled: checked })}
            >
              <FieldCell shown={form.renderCommentsEnabled}>
                <SearchableSelect
                  id={id('render-comments-selector')}
                  options={COMMENTS_RENDER_TYPES}
                  value={form.renderComments}
                  onChange={(value) => onPatch({ renderComments: value })}
                  disabled={busy}
                />
              </FieldCell>
            </SwitchRow>
            {form.renderCommentsEnabled && (
              <div className="property-wrapper sub-row" id={id('render-comments-options')}>
                <FieldCell wide>
                  <div className="option-pair">
                    <label htmlFor={id('include-unreferenced-comments')} title={UNREFERENCED_COMMENTS_HELP}>
                      <input
                        id={id('include-unreferenced-comments')}
                        type="checkbox"
                        checked={form.includeUnreferencedComments}
                        onChange={(event) => onPatch({ includeUnreferencedComments: event.target.checked })}
                      />
                      include unreferenced
                    </label>
                  </div>
                </FieldCell>
              </div>
            )}
          </div>

          {/* What the document's links pull in, which is a question of its own - and two controls wide, so
              it takes a line to itself between the hairlines rather than a place in a list. */}
          {rolesShown && data && (
            <div className="docx-section single-column group-start">
              <SwitchRow
                rowId={id('roles-wrapper')}
                id={id('selected-roles')}
                label="Specific Workitem roles"
                checked={form.rolesEnabled}
                onChange={(checked) => onPatch({ rolesEnabled: checked })}
              >
                {form.rolesEnabled && (
                  <FieldCell grows>
                    {/* Two controls in one cell, side by side while the form has room for both. */}
                    <div className="option-pair">
                      <SearchableSelect
                        id={id('roles-selector')}
                        multiple
                        options={data.roles}
                        value={form.linkedWorkitemRoles}
                        onChange={(values) => onPatch({ linkedWorkitemRoles: values })}
                        disabled={busy}
                      />
                      <SearchableSelect
                        id={id('roles-direction-selector')}
                        options={LINK_ROLE_DIRECTIONS}
                        value={form.linkRoleDirection}
                        onChange={(value) => onPatch({ linkRoleDirection: value })}
                        disabled={busy}
                      />
                    </div>
                  </FieldCell>
                )}
              </SwitchRow>
            </div>
          )}

          {/* The two rows that carry a value the user types. One column whatever the form's width, so each
              of them has the whole width for its field. */}
          <div className="docx-section single-column group-start">
            <SwitchRow
              className="tight"
              id={id('specific-chapters')}
              label="Specific higher level chapters"
              checked={form.specificChaptersEnabled}
              onChange={(checked) => onPatch({ specificChaptersEnabled: checked })}
            >
              <FieldCell grows shown={form.specificChaptersEnabled}>
                <input
                  id={id('chapters')}
                  className={invalidField === 'chapters' ? 'error' : undefined}
                  type="text"
                  placeholder="eg. 1,2,4 etc."
                  value={form.specificChapters}
                  onChange={(event) => onPatch({ specificChapters: event.target.value })}
                />
              </FieldCell>
            </SwitchRow>
            <FieldRow
              label={
                <>
                  Removal selector:
                  <span className="more-info" title={REMOVAL_SELECTOR_HELP} />
                </>
              }
              labelFor={id('removal-selector')}
            >
              <FieldCell grows>
                <input
                  id={id('removal-selector')}
                  type="text"
                  value={form.removalSelector}
                  onChange={(event) => onPatch({ removalSelector: event.target.value })}
                />
              </FieldCell>
            </FieldRow>
          </div>
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
