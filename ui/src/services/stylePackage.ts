import type { SelectOption } from '@grigoriev/react-sbb-polarion';

/**
 * The content document of one named `style-package` configuration, field for field as
 * `StylePackageModel.java` stores it.
 *
 * A field the administration page can switch off is stored as `null`, which is what "not overridden"
 * means to the exporter: the document then takes the value from the reference template, or from the
 * defaults of the conversion.
 */
export interface StylePackageSettings {
  matchingQuery?: string | null;
  weight?: number | null;
  exposeSettings?: boolean;
  template?: string | null;
  localization?: string | null;
  orientation?: string | null;
  paperSize?: string | null;
  imageDensity?: string | null;
  preserveTableStyles?: boolean;
  webhooks?: string | null;
  removalSelector?: string | null;
  renderComments?: string | null;
  includeUnreferencedComments?: boolean;
  cutEmptyChapters?: boolean;
  cutEmptyWorkitemAttributes?: boolean;
  cutLocalURLs?: boolean;
  specificChapters?: string | null;
  language?: string | null;
  linkedWorkitemRoles?: string[] | null;
  linkRoleDirection?: string | null;
}

/** The name every settings feature has, and the fallback of every child dropdown. */
export const DEFAULT_NAME = 'Default';

/** The settings a style package points at by name. Each is a page of its own. */
export const CHILD_SETTINGS = ['templates', 'localization', 'webhooks'] as const;
export type ChildSetting = (typeof CHILD_SETTINGS)[number];
export type ChildNames = Record<ChildSetting, SelectOption[]>;

export const NO_CHILD_NAMES: ChildNames = { templates: [], localization: [], webhooks: [] };

export const ORIENTATIONS: SelectOption[] = [
  { id: 'PORTRAIT', name: 'Portrait' },
  { id: 'LANDSCAPE', name: 'Landscape' },
];

// The ids are stored verbatim and handed to pandoc as its `paper_size`, so they may not be prettified.
// The labels are the readable form of the enum constants the legacy dropdown showed as they are.
export const PAPER_SIZES: SelectOption[] = [
  { id: 'A5', name: 'A5' },
  { id: 'A4', name: 'A4' },
  { id: 'A3', name: 'A3' },
  { id: 'B5', name: 'B5' },
  { id: 'B4', name: 'B4' },
  { id: 'JIS_B5', name: 'JIS-B5' },
  { id: 'JIS_B4', name: 'JIS-B4' },
  { id: 'LETTER', name: 'Letter' },
  { id: 'LEGAL', name: 'Legal' },
  { id: 'LEDGER', name: 'Ledger' },
];

export const IMAGE_DENSITIES: SelectOption[] = [
  { id: 'DPI_96', name: '96 dpi' },
  { id: 'DPI_192', name: '192 dpi' },
  { id: 'DPI_300', name: '300 dpi' },
  { id: 'DPI_600', name: '600 dpi' },
];

export const COMMENTS_RENDER_TYPES: SelectOption[] = [
  { id: 'OPEN', name: 'Open' },
  { id: 'ALL', name: 'All' },
];

export const LINK_ROLE_DIRECTIONS: SelectOption[] = [
  { id: 'BOTH', name: 'Both directions' },
  { id: 'DIRECT', name: 'Direct only' },
  { id: 'REVERSE', name: 'Reverse only' },
];

export const LANGUAGES: SelectOption[] = [
  { id: 'de', name: 'Deutsch' },
  { id: 'fr', name: 'Français' },
  { id: 'it', name: 'Italiano' },
];

/** What a control falls back to when the stored document leaves it off. */
export const DEFAULT_ORIENTATION = 'PORTRAIT';
export const DEFAULT_PAPER_SIZE = 'A4';
export const DEFAULT_IMAGE_DENSITY = 'DPI_96';
export const DEFAULT_RENDER_COMMENTS = 'OPEN';
export const DEFAULT_LINK_ROLE_DIRECTION = 'BOTH';
export const DEFAULT_LANGUAGE = 'de';

/** The weight a freshly created style package starts at. */
export const DEFAULT_WEIGHT = '50';

export const WEIGHT_HELP =
  'A float number from 0.0 to 100, which will determine the position of current style package in the ' +
  'resulting style packages list. The higher the number, the higher its position will be.';

export const MATCHING_QUERY_HELP =
  'A query to select documents to which this style package will be relevant. For documents not matching ' +
  "this query the style package won't be visible. If you want to make this style package be available to " +
  'all documents, just leave this field empty.';

export const REMOVAL_SELECTOR_HELP =
  'CSS-like selector(s) for elements to remove. Example: img.specificClass removes images with that class. ' +
  'Separate multiple selectors with commas, e.g. img.specificClass, table.unwanted, div#ad-banner.';

export const UNREFERENCED_COMMENTS_HELP = 'Unreferenced comments will be rendered at the end of the document';
