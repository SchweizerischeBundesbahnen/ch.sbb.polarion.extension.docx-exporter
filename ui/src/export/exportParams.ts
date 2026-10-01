import type { ExportForm } from './exportForm';
import { CHAPTERS_ERROR, parseChapters } from './validation';

/**
 * The export request the conversion endpoints take, built from an export dialog's form.
 *
 * The field names are the product `ExportParams`' own (`cutEmptyWIAttributes`, `cutLocalUrls`, ...), so
 * this is the same body the legacy `ExportPanel.buildRequestJson()` produced. Whatever is switched off is
 * left out rather than sent as null, which is what the legacy `toJSON()` did with its null filter.
 *
 * There is no `documentType`: every DOCX export is a Live Document, and `ExportParams.java` has no such
 * field. The legacy builder carried one on the builder object, but `ExportParams`' constructor never
 * copied it, so it was never serialized either.
 */
export interface ExportParamsJson {
  projectId?: string | null;
  locationPath?: string | null;
  baselineRevision?: string | null;
  revision?: string | null;
  template?: string;
  localization?: string;
  webhooks?: string | null;
  orientation?: string | null;
  paperSize?: string | null;
  imageDensity?: string | null;
  preserveTableStyles?: boolean;
  renderComments?: string | null;
  includeUnreferencedComments?: boolean;
  cutEmptyChapters?: boolean;
  cutEmptyWIAttributes?: boolean;
  cutLocalUrls?: boolean;
  chapters?: string[] | null;
  language?: string | null;
  linkedWorkitemRoles?: string[];
  linkRoleDirection?: string | null;
  removalSelector?: string;
  fileName?: string;
  urlQueryParameters?: Record<string, string>;
}

/** Where the document being exported lives, as the page URL says. */
export interface DocumentContext {
  projectId?: string | null;
  locationPath?: string | null;
  baselineRevision?: string | null;
  revision?: string | null;
  urlQueryParameters?: Record<string, string>;
}

/** Which field a validation error belongs to, so the form can mark it. */
export type ExportField = 'chapters';

export interface ExportValidationError {
  field: ExportField;
  message: string;
}

export type BuildResult = { params: ExportParamsJson } | { error: ExportValidationError };

/**
 * The export request, or the validation error standing in its way.
 *
 * The chapters entry is the one thing that can stop the build; the dialog then marks that field and
 * shows the message, as it always did.
 */
export function buildExportParams(form: ExportForm, context: DocumentContext, fileName?: string): BuildResult {
  let chapters: string[] | null = null;
  if (form.specificChaptersEnabled) {
    const parsed = parseChapters(form.specificChapters);
    if (!parsed) {
      return { error: { field: 'chapters', message: CHAPTERS_ERROR } };
    }
    chapters = parsed;
  }

  const roles = form.rolesEnabled ? form.linkedWorkitemRoles : [];

  return {
    params: {
      projectId: context.projectId,
      locationPath: context.locationPath,
      baselineRevision: context.baselineRevision,
      revision: context.revision,
      template: form.template,
      localization: form.localization,
      webhooks: form.webhooksEnabled ? form.webhooks : null,
      // The three page setup fields are the DOCX-specific ones: left out, they mean "take what the
      // reference template says", which is not the same as sending the control's current value.
      orientation: form.orientationEnabled ? form.orientation : null,
      paperSize: form.paperSizeEnabled ? form.paperSize : null,
      imageDensity: form.imageDensityEnabled ? form.imageDensity : null,
      preserveTableStyles: form.preserveTableStyles,
      renderComments: form.renderCommentsEnabled ? form.renderComments : null,
      includeUnreferencedComments: form.renderCommentsEnabled && form.includeUnreferencedComments,
      cutEmptyChapters: form.cutEmptyChapters,
      cutEmptyWIAttributes: form.cutEmptyWorkitemAttributes,
      cutLocalUrls: form.cutLocalURLs,
      chapters,
      language: form.localizeEnums ? form.language : null,
      linkedWorkitemRoles: roles,
      // Only meaningful next to a role: the legacy panel read the direction control solely inside the
      // branch that collected the roles, and sent null otherwise.
      linkRoleDirection: form.rolesEnabled ? form.linkRoleDirection : null,
      removalSelector: form.removalSelector,
      fileName,
      urlQueryParameters: context.urlQueryParameters,
    },
  };
}

/**
 * The request body for the conversion endpoints: the params as JSON, without what is not set.
 *
 * The product's `ExportParams.toJSON()` filtered null and undefined out before stringifying, and the
 * server relies on it - a `null` orientation and an absent one do not mean the same thing to every field.
 */
export function toRequestBody(params: ExportParamsJson | Record<string, unknown>): string {
  const defined = Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== null && value !== undefined),
  );
  return JSON.stringify(defined, null, 2);
}
