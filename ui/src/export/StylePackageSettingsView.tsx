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

export interface StylePackageSettingsViewProps {
  /** What every element id is prefixed with: `popup-` in the dialog, nothing in the panel and on the administration page. */
  ids: string;
  form: ExportForm;
  onPatch: (values: Partial<ExportForm>) => void;
  /** The named configurations the child dropdowns offer, null until they are read. */
  childNames: ChildNames | null;
  /** The link roles of the project. None means the roles row is not offered, unless {@link rolesAlwaysOffered}. */
  roles: SelectOption[];
  /**
   * The roles row is offered even without a role to pick: the administration page, where the global scope has no
   * project to read roles from and a package can still store them, and has to be able to switch them off.
   */
  rolesAlwaysOffered?: boolean;
  webhooksEnabled: boolean;
  busy: boolean;
  /** The field an export was refused on, which is then marked. */
  invalidField?: ExportField | null;
}

/**
 * The settings of a style package, laid out as rows against one set of columns (see export-form-layout.css).
 *
 * The "Export to DOCX" dialog and the Document Properties side panel render them inside the export form, where a
 * package exposes its settings, and the Style Packages administration page renders them as the package itself
 * (#423). One markup is what keeps the three looking the same: the page used to be laid out by hand, apart from
 * the two others, and drifted from them.
 */
export default function StylePackageSettingsView({
  ids,
  form,
  onPatch,
  childNames,
  roles,
  rolesAlwaysOffered,
  webhooksEnabled,
  busy,
  invalidField,
}: Readonly<StylePackageSettingsViewProps>) {
  const id = (name: string): string => `${ids}${name}`;
  const childOptions = (setting: ChildSetting): SelectOption[] => childNames?.[setting] ?? [];
  const rolesShown = roles.length > 0 || !!rolesAlwaysOffered;

  return (
    <>
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
        {webhooksEnabled && (
          <SwitchRow
            id={id('webhooks-checkbox')}
            label="Webhooks:"
            checked={form.webhooksEnabled}
            onChange={(checked) => onPatch({ webhooksEnabled: checked })}
          >
            <FieldCell shown={form.webhooksEnabled}>
              <SearchableSelect
                id={id('webhooks-selector')}
                ariaLabel="Webhooks"
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
                ariaLabel="Paper size"
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
                ariaLabel="Orientation"
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
                ariaLabel="Image density"
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
              ariaLabel="Language"
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
              ariaLabel="Comments rendering"
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
      {rolesShown && (
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
                    ariaLabel="Workitem roles"
                    multiple
                    options={roles}
                    value={form.linkedWorkitemRoles}
                    onChange={(values) => onPatch({ linkedWorkitemRoles: values })}
                    disabled={busy}
                  />
                  <SearchableSelect
                    id={id('roles-direction-selector')}
                    ariaLabel="Link role direction"
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
    </>
  );
}
