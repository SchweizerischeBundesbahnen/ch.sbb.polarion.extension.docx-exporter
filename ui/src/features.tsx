import type { ComponentType } from 'react';
import About from './pages/About';
import Authorization from './pages/Authorization';
import Disclaimer from './pages/Disclaimer';
import FilenameTemplate from './pages/FilenameTemplate';
import Localization from './pages/Localization';
import SidePanelPreview from './pages/SidePanelPreview';
import StylePackageWeights from './pages/StylePackageWeights';
import StylePackages from './pages/StylePackages';
import Templates from './pages/Templates';
import UserGuide from './pages/UserGuide';
import Webhooks from './pages/Webhooks';

/**
 * A single navigable page of the app. The `id` is what appears in the URL as `?feature=<id>` and is
 * also what `hivemodule.xml` points its admin extenders at, so the ids here and the extender ids must
 * stay identical - a typo is a blank page in Polarion and no test catches it.
 *
 * Every administration entry of the extension is served from here; the legacy `docx-exporter-admin`
 * webapp no longer exists. The one menu entry with no feature of its own is REST API, which opens the
 * generated Swagger UI.
 */
export interface Feature {
  id: string;
  label: string;
  description: string;
  component: ComponentType;
}

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
  {
    id: 'user-guide',
    label: 'User Guide',
    description: 'How to use the extension, generated from USER_GUIDE.md.',
    component: UserGuide,
  },
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
  // Not an administration page: `hivemodule.xml` points at none of it. It is reachable only by typing
  // `?feature=side-panel`, which is what the ` (dev)` in the label says, and it needs a Polarion behind
  // VITE_BASE_URL to show anything.
  {
    id: 'side-panel',
    label: 'Document Properties side panel (dev)',
    description: 'The export panel of the document editor, run against a real document.',
    component: SidePanelPreview,
  },
];

export function findFeature(id: string | null): Feature | undefined {
  return FEATURES.find((f) => f.id === id);
}
