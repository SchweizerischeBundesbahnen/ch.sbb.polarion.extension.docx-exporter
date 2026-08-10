import type { SelectOption } from '@grigoriev/react-sbb-polarion';
import type { PopupData } from '../src/export/exportData';
import type { ExportPopupDependencies } from '../src/popup/ExportPopupModal';
import type { ConversionResult } from '../src/services/conversion';
import type { DocumentIdentity } from '../src/services/exportContext';
import type { StylePackageSettings } from '../src/services/stylePackage';
import { SAMPLE_STYLE_PACKAGE } from './sidePanelSamples';

/**
 * The export dialog filled in without a Polarion behind it: the fixture the dialog's suites share.
 *
 * The dialog reads its data over REST and runs a conversion, neither of which a browser test has. This
 * stands in for both, so the behavior suite and the visual references describe the same dialog.
 *
 * The style packages are `sidePanelSamples`' own, not copies: the two surfaces offer the same settings and
 * read them through the same `export/` model, so a package that describes one describes the other.
 */

const NAMES: SelectOption[] = [
  { id: 'Default', name: 'Default' },
  { id: 'SBB', name: 'SBB' },
];

/** The document the editor toolbar opens the dialog for. */
export const SAMPLE_DOCUMENT: DocumentIdentity = {
  scope: 'project/elibrary/',
  projectId: 'elibrary',
  locationPath: 'Default Space/Cross Link Issue',
  spaceId: 'Default Space',
  documentName: 'Cross Link Issue',
  urlQueryParameters: {},
};

export const SAMPLE_POPUP_DATA: PopupData = {
  stylePackages: [
    { id: 'Default', name: 'Default' },
    { id: 'Specification', name: 'Specification' },
  ],
  childNames: {
    templates: NAMES,
    localization: NAMES,
    webhooks: NAMES,
  },
  roles: [
    { id: 'relates_to', name: 'relates_to' },
    { id: 'depends_on', name: 'depends_on' },
    { id: 'verifies', name: 'verifies' },
  ],
  fileName: 'E-Library Cross Link Issue.docx',
  documentLanguage: 'de',
  webhooksEnabled: false,
};

/** A conversion result, as a finished job produces one. */
export const docxResult = (warning: string | null = null): ConversionResult => ({
  blob: new Blob(['docx']),
  fileName: null,
  warning,
});

/** A conversion that never finishes, so the dialog can be looked at while an export is running. */
const NEVER_COMPLETES = (): Promise<ConversionResult> => new Promise<ConversionResult>(() => {});

export interface PopupSampleOptions {
  /** The style package the dialog loads. Defaults to the one the screenshots were taken with. */
  stylePackage?: StylePackageSettings;
  /** Fields of the dialog's data to override (webhooks, roles, style packages, ...). */
  data?: Partial<PopupData>;
  /** Fails the whole read, which is what leaves the dialog unusable. */
  loadError?: Error;
  /** What an export does, given the request body. Left out, it never completes: the in-progress state. */
  convert?: (request: string) => Promise<ConversionResult>;
  download?: (blob: Blob, fileName: string) => void;
}

/**
 * The REST routes the dialog reads, for the suite that mounts it through `openExportPopup` rather than
 * rendering it with stubbed dependencies. Shaped as `mockFetch` wants them.
 */
export const popupRoutes = (stylePackage: StylePackageSettings = SAMPLE_STYLE_PACKAGE) => {
  const names = (...values: string[]) => values.map((name) => ({ name, scope: 'project/elibrary/' }));
  return [
    { method: 'POST', match: /\/settings\/style-package\/suitable-names/, json: names('Default', 'SBB') },
    { method: 'GET', match: /\/settings\/style-package\/names\/[^/]+\/content/, json: stylePackage },
    { method: 'GET', match: /\/settings\/templates\/names/, json: names('Default') },
    { method: 'GET', match: /\/settings\/localization\/names/, json: names('Default') },
    { method: 'GET', match: /\/settings\/webhooks\/names/, json: names('Default') },
    { method: 'GET', match: /\/link-role-names/, json: ['relates_to'] },
    { method: 'POST', match: /\/export-filename/, respond: () => new Response('E-Library Cross Link Issue.docx') },
    { method: 'GET', match: /\/document-language/, respond: () => new Response('de') },
    { method: 'GET', match: /\/webhooks\/status/, json: { enabled: false } },
  ];
};

/** Dependencies that answer from the sample data instead of the network. */
export function popupDependencies(options: PopupSampleOptions = {}): ExportPopupDependencies {
  const convert = options.convert ?? NEVER_COMPLETES;
  return {
    loadData: () =>
      options.loadError
        ? Promise.reject(options.loadError)
        : Promise.resolve({ ...SAMPLE_POPUP_DATA, ...options.data }),
    loadPackage: () => Promise.resolve(options.stylePackage ?? SAMPLE_STYLE_PACKAGE),
    convert: (_remote, request) => convert(request),
    download: options.download ?? (() => {}),
  };
}
