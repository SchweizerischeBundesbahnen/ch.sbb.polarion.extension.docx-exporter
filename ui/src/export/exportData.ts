import type { SelectOption, SendRequest, SettingName } from '@grigoriev/react-sbb-polarion';
import type { DocumentIdentity } from '../services/exportContext';
import { CHILD_SETTINGS, type ChildNames, NO_CHILD_NAMES, type StylePackageSettings } from '../services/stylePackage';
import { toRequestBody } from './exportParams';

/**
 * Everything the export panel needs before it can be shown, read over REST.
 *
 * `DocxExporterFormExtension` used to render the panel's markup with the style packages, the setting
 * names, the link roles, the file name and the export permission already substituted into it. None of
 * that reaches a React panel, so the same values are read from the endpoints the DLE toolbar popup has
 * always read them from.
 */

/** One item the style packages are chosen for, in the shape `/settings/style-package/suitable-names` wants. */
export interface DocIdentifier {
  projectId?: string;
  spaceId?: string;
  /** Never omitted: the server dereferences it without a null check. */
  documentName: string;
}

/**
 * What `/permissions/export` said, including that it did not answer.
 *
 * `unknown` is not `denied`: both keep the export button disabled, but only `denied` is something the
 * panel can tell the user a reason for. See {@link loadExportPermission} for why the failure is not read
 * as granted.
 */
export type ExportPermission = 'granted' | 'denied' | 'unknown';

async function readJson<T>(sendRequest: SendRequest, method: string, url: string, body?: string): Promise<T> {
  const response = await sendRequest({
    method,
    url,
    body,
    contentType: body === undefined ? undefined : 'application/json',
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return (await response.json()) as T;
}

async function readText(sendRequest: SendRequest, method: string, url: string, body?: string): Promise<string> {
  const response = await sendRequest({
    method,
    url,
    body,
    contentType: body === undefined ? undefined : 'application/json',
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return await response.text();
}

const toOptions = (names: SettingName[]): SelectOption[] => names.map((name) => ({ id: name.name, name: name.name }));

/** The document identifier the style package endpoint wants, from where the page says the document is. */
export const toDocIdentifier = (document: DocumentIdentity): DocIdentifier => ({
  ...(document.projectId ? { projectId: document.projectId } : {}),
  ...(document.spaceId ? { spaceId: document.spaceId } : {}),
  documentName: document.documentName ?? '',
});

/**
 * The style packages offered for the given documents, best match first (the server orders them by
 * weight), which is why the panel preselects the head of this list.
 *
 * The endpoint takes a list because a bulk export asks for the packages that suit **every** selected
 * document.
 */
export function loadStylePackageNames(sendRequest: SendRequest, identifiers: DocIdentifier[]): Promise<SelectOption[]> {
  return readJson<SettingName[]>(
    sendRequest,
    'POST',
    '/settings/style-package/suitable-names',
    JSON.stringify(identifiers),
  ).then(toOptions);
}

/** The content of one style package. */
export function loadStylePackage(sendRequest: SendRequest, name: string, scope: string): Promise<StylePackageSettings> {
  return readJson<StylePackageSettings>(
    sendRequest,
    'GET',
    `/settings/style-package/names/${encodeURIComponent(name)}/content?scope=${encodeURIComponent(scope)}`,
  );
}

/** The names one child setting offers in the given scope. */
export function loadSettingNames(sendRequest: SendRequest, setting: string, scope: string): Promise<SelectOption[]> {
  return readJson<SettingName[]>(
    sendRequest,
    'GET',
    `/settings/${setting}/names?scope=${encodeURIComponent(scope)}`,
  ).then(toOptions);
}

/** The work item link roles of a scope. Empty means the roles row is not offered at all. */
export function loadLinkRoles(sendRequest: SendRequest, scope: string): Promise<SelectOption[]> {
  return readJson<string[]>(sendRequest, 'GET', `/link-role-names?scope=${encodeURIComponent(scope)}`).then((roles) =>
    roles.map((role) => ({ id: role, name: role })),
  );
}

/** Whether webhooks are enabled installation-wide; the webhooks row is hidden when they are not. */
export function loadWebhooksEnabled(sendRequest: SendRequest): Promise<boolean> {
  return readJson<{ enabled?: boolean }>(sendRequest, 'GET', '/webhooks/status').then((status) => !!status?.enabled);
}

/** The document's `docLanguage` custom field. Returns null when the field is unset. */
export async function loadDocumentLanguage(
  sendRequest: SendRequest,
  document: DocumentIdentity,
): Promise<string | null> {
  const parameters = new URLSearchParams({
    projectId: document.projectId ?? '',
    spaceId: document.spaceId ?? '',
    documentName: document.documentName ?? '',
  });
  if (document.revision) {
    parameters.set('revision', document.revision);
  }
  const language = await readText(sendRequest, 'GET', `/document-language?${parameters.toString()}`);
  return language || null;
}

/** The default file name for an export, as `/export-filename` builds it from the filename template. */
export function loadExportFileName(sendRequest: SendRequest, params: Record<string, unknown>): Promise<string> {
  return readText(sendRequest, 'POST', '/export-filename', toRequestBody(params));
}

/**
 * Whether the current user may export this project at all.
 *
 * Fails closed, the way the DLE toolbar's export button already does: generic's `dle-toolbar-starter.js`
 * documents `permitted !== true (or a non-OK status / error) disables the button (fail-closed)`.
 * Anything but an explicit `true` - a malformed body included - is therefore not a grant, and a read that
 * failed is `unknown` rather than `denied` so the panel does not claim a reason it does not have.
 */
export function loadExportPermission(sendRequest: SendRequest, projectId: string | null): Promise<ExportPermission> {
  return readJson<{ permitted?: boolean }>(
    sendRequest,
    'GET',
    `/permissions/export?projectId=${encodeURIComponent(projectId ?? '')}`,
  )
    .then((permission): ExportPermission => (permission?.permitted === true ? 'granted' : 'denied'))
    .catch((): ExportPermission => 'unknown');
}

/** The names every child dropdown offers, read in one round. */
async function loadChildNames(sendRequest: SendRequest, scope: string): Promise<ChildNames> {
  const entries = await Promise.all(
    CHILD_SETTINGS.map(async (setting) => [setting, await loadSettingNames(sendRequest, setting, scope)] as const),
  );
  return { ...NO_CHILD_NAMES, ...Object.fromEntries(entries) } as ChildNames;
}

/** What the document properties side panel needs. */
export interface PanelData {
  stylePackages: SelectOption[];
  childNames: ChildNames;
  roles: SelectOption[];
  fileName: string;
  documentLanguage: string | null;
  webhooksEnabled: boolean;
  exportPermission: ExportPermission;
}

/**
 * Reads the side panel's data in one round.
 *
 * The reads that decide what the panel *looks* like - the option lists and the style packages - are
 * required: without them there is nothing to choose from, so a failure here is reported instead of an
 * empty dropdown. The four that only decide a detail (link roles, file name, document language, and
 * whether the user may export) fall back rather than fail: an unreachable `/export-filename` is no
 * reason to withhold a panel whose file name the user can type themselves.
 */
export async function loadPanelData(sendRequest: SendRequest, document: DocumentIdentity): Promise<PanelData> {
  const [stylePackages, childNames, roles] = await Promise.all([
    loadStylePackageNames(sendRequest, [toDocIdentifier(document)]),
    loadChildNames(sendRequest, document.scope),
    loadLinkRoles(sendRequest, document.scope).catch(() => []),
  ]);

  const [fileName, documentLanguage, webhooksEnabled, exportPermission] = await Promise.all([
    loadExportFileName(sendRequest, {
      projectId: document.projectId,
      locationPath: document.locationPath,
      revision: document.revision,
    }).catch(() => ''),
    loadDocumentLanguage(sendRequest, document).catch(() => null),
    loadWebhooksEnabled(sendRequest).catch(() => false),
    loadExportPermission(sendRequest, document.projectId ?? null),
  ]);

  return { stylePackages, childNames, roles, fileName, documentLanguage, webhooksEnabled, exportPermission };
}
