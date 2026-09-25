import { DocPage, type Feature as RoutedFeature, findFeature as findIn } from '@sbb-polarion/react-sbb-polarion';
import { DOC_ORDER } from './docs/manifest';
import About from './pages/About';
import Authorization from './pages/Authorization';
import Disclaimer from './pages/Disclaimer';
import ExportPopupPreview from './pages/ExportPopupPreview';
import FilenameTemplate from './pages/FilenameTemplate';
import Localization from './pages/Localization';
import SidePanelPreview from './pages/SidePanelPreview';
import StylePackageWeights from './pages/StylePackageWeights';
import StylePackages from './pages/StylePackages';
import Templates from './pages/Templates';
import Webhooks from './pages/Webhooks';

/**
 * A single navigable page of the app. The `id` is what appears in the URL as `?feature=<id>` and is
 * also what `hivemodule.xml` points its admin extenders at, so the ids here and the extender ids must
 * stay identical - a typo is a blank page in Polarion and no test catches it.
 *
 * Every administration entry of the extension is served from here; the legacy `docx-exporter-admin`
 * webapp no longer exists. The one menu entry with no feature of its own is REST API, which opens the
 * generated Swagger UI.
 *
 * Extends RSP's routed `Feature` (`id` + `component`, all FeatureRouter needs) with what the Landing page lists.
 */
export interface Feature extends RoutedFeature {
  label: string;
  description: string;
}

// The documentation-site articles, derived from the shared manifest (docs.config.json) rather than listed by
// hand: each renders through the single DocPage bound to its manifest entry, in the manifest's reading order.
// Adding an article is a docs.config.json item (plus its markdown and the pom render) - nothing here changes.
// They carry no hivemodule.xml menu entry of their own: they are reached from the single `documentation` node
// and the articles' cross-document links (rewritten to ?feature=<id> by the interceptor in App.tsx); the ids
// equal the generated html basenames those links point at.
const DOC_FEATURES: Feature[] = DOC_ORDER.map((doc) => {
  const Component = () => <DocPage doc={doc} />;
  Component.displayName = `DocPage(${doc.id})`;
  return {
    id: doc.id,
    label: doc.title,
    description: `${doc.title}, generated from ${doc.source}.`,
    component: Component,
  };
});

export const FEATURES: Feature[] = [
  {
    id: 'about',
    label: 'About',
    description: 'Extension version and general information.',
    component: About,
  },
  {
    id: 'disclaimer',
    label: 'Usage Disclaimer',
    description: 'The terms this extension is provided under.',
    component: Disclaimer,
  },
  // The documentation site, in manifest reading order (Quick Start, User Guide, Configuration, ...).
  ...DOC_FEATURES,
  {
    id: 'filename',
    label: 'Filename template',
    description: 'The Velocity template exported documents are named after.',
    component: FilenameTemplate,
  },
  {
    id: 'style-package',
    label: 'Style Package',
    description: 'Everything one export is driven by, from the reference template to the conversion switches.',
    component: StylePackages,
  },
  {
    id: 'templates',
    label: 'Templates',
    description: 'The reference DOCX whose styles the exported document is built on.',
    component: Templates,
  },
  {
    id: 'localization',
    label: 'Localization',
    description: 'German, French and Italian translations of the exported work item fields.',
    component: Localization,
  },
  {
    id: 'webhooks',
    label: 'Webhooks',
    description: 'REST endpoints the generated HTML is passed through before it is converted.',
    component: Webhooks,
  },
  {
    id: 'style-package-weights',
    label: 'Style Package Weights',
    description: 'Order the style packages; the top one is preselected on the export panel.',
    component: StylePackageWeights,
  },
  {
    id: 'authorization',
    label: 'Authorization',
    description: 'Configure which global and project roles are allowed to export.',
    component: Authorization,
  },
  // Not administration pages: `hivemodule.xml` points at neither. They are reachable only by typing
  // `?feature=side-panel` or `?feature=export-popup`, which is what the ` (dev)` in the labels says, and
  // both need a Polarion behind VITE_BASE_URL to show anything.
  {
    id: 'side-panel',
    label: 'Document Properties side panel (dev)',
    description: 'The export panel of the document editor, run against a real document.',
    component: SidePanelPreview,
  },
  {
    id: 'export-popup',
    label: 'Export to DOCX dialog (dev)',
    description: 'The dialog the document editor toolbar button opens, run against a real document.',
    component: ExportPopupPreview,
  },
];

export function findFeature(id: string | null): Feature | undefined {
  return findIn(FEATURES, id);
}
