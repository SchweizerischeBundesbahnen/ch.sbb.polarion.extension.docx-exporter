# CLAUDE.md

## Gotchas

- **A dependency can block Polarion startup through its manifest alone.** Polarion 2606 scans every
  nested jar and rejects both a class that references a forbidden package and a `META-INF/MANIFEST.MF`
  whose attribute value *contains* one, so an OSGi `uses:="javax.annotation.processing,..."` counts as
  `javax.annotation` and the whole extension is reported as "not Jakarta compatible" - no CI job sees
  this, only a local deploy. That is why `tika.version` stays on 3.x (`renovate.json` holds it there);
  tika-core 4.0.0 trips it. Check a bump with a real deploy, or
  `unzip -p <nested>.jar META-INF/MANIFEST.MF | grep javax`.
- **All administration pages are React now.** They were converted to
  [react-sbb-polarion](https://github.com/SchweizerischeBundesbahnen/react-sbb-polarion) one at a time, and
  `docx-exporter-app` (the Vite bundle in `ui/`, see [`ui/README.md`](ui/README.md)) serves every one of
  them. `hivemodule.xml` carries a `pageUrl` per menu entry; the ids there must match
  `ui/src/features.tsx` - a mismatch is a blank page and no test catches it. The legacy
  `docx-exporter-admin` webapp is gone: its menu icons moved to `webapp/docx-exporter-app/images/`, so
  two webapps remain - `docx-exporter` (REST + the toolbar injectors) and `docx-exporter-app`.

- **`docx-exporter-app` also serves two surfaces that are not administration pages**, each a Vite entry of
  its own with a **fixed** file name (their importers name them by URL) kept exporting by
  `preserveEntrySignatures: 'strict'` and guarded by `ui/scripts/check-runtime-entries.mjs`:
  - **Document Properties side panel** - `DocxExporterFormExtension` emits only a fragment (an empty
    `#docx-exporter-panel` div plus a `<link>` to `css/starter.css` whose `onload` fires the import) and
    `assets/side-panel.js` mounts React into a shadow root of it. It reads its data from the same internal
    REST endpoints the export dialog uses; the Java side substitutes nothing but the bundle version.
    Its CSS is `ui/src/sidepanel/side-panel.css`.
  - **"Export to DOCX" dialog** - `assets/export-popup.js` exporting `openExportPopup()`, imported on click
    by `js/starter.js`. It appends its own host to the page body and mounts into a shadow root of it.
    Its CSS is `ui/src/popup/export-popup.css`. There is no `documentType`: this extension exports Live
    Documents only, and `ExportParams.java` has no such field.

  Each shadow root carries its own CSS, so the only stylesheet the extension still puts on a Polarion page
  is the **empty** `css/starter.css`, and it is there to fire the side panel's `onload`, not to style
  anything. `css/docx-exporter.css` is deleted and the injector calls no `injectStyle`; the toolbar button
  uses Polarion's own classes plus generic's `css/dle-toolbar.css`.

- **`webapp/docx-exporter/js/modules/` is gone.** `ExportPopup.js`, `ExportPanel.js`, `ExportContext.js`
  and `ExportParams.js` were ported into the app: `ui/src/export/` (the shared export model - a style
  package read into a form, a form turned into a request, the REST reads each surface needs),
  `ui/src/services/exportContext.ts` (the location hash) and `ui/src/services/conversion.ts` (the
  convert-job protocol). Nothing is loaded across webapps at runtime any more. What is left in
  `webapp/docx-exporter` is the two injector scripts, the empty `css/starter.css` trigger and the two HTML
  templates the Java side reads server-side (`sidePanelContent.html`, `docxTemplate.html`).

- **The UI build comes from the generic parent**, activated by the presence of `ui/package.json` (its
  `vite-ui` profile): `npm ci` + `npm run build`, the bundle copied into `webapp/docx-exporter-app/`, and
  the JS suite in the Maven `test` phase. This pom adds nothing for it beyond pinning
  `frontend-maven-plugin.version`, which the parent's profile reads. Note it also redirects
  markdown2html's output (`about.html`, `user-guide.html`, `disclaimer.html`) into
  `webapp/docx-exporter-app/html/`.

- **There is one JS toolchain, and it lives in `ui/`.** The root `package.json`, `package-lock.json`,
  `node/`, `node_modules/`, `src/test/js/` and this pom's own `frontend-maven-plugin` block are gone. The
  mocha suite that tested the toolbar injectors is now `ui/test/starterInjector.node.test.ts` and
  `ui/test/dleToolbar.node.test.ts`, run by the **`node`** project of `ui/vitest.config.ts` (jsdom) next to
  the **`browser`** project that tests the app. Injector tests must stay in the `node` project: those
  scripts drive the top frame, and Vitest browser mode runs each file in an iframe and keeps `top` for its
  own runner page. Name them `*.node.test.ts` - that suffix is what routes a file between the two projects.

- **`mvn verify` silently skips the entire Pandoc conversion test suite.** `BasePandocTest` is `@SkipTestWhenParamNotSet` keyed on the `docxExporterImpl` system property, so a plain build reports green without ever running the real HTML→DOCX tests. To actually run them, use `mvn verify -P tests-with-pandoc-docker -Dpandoc.service.url=<url>` (the profile sets `docxExporterImpl=docker`) with a reachable [pandoc-service](https://github.com/SchweizerischeBundesbahnen/pandoc-service) container. This is what CI does.

- **Template-sync: never blind-copy `maven-build.yml` from `open-source-polarion-java-repo-template`.** It carries pandoc-specific config the template lacks, and a verbatim copy silently breaks CI: `maven-build.yml` has the `pandoc` service container plus `-P tests-with-pandoc-docker -Dpandoc.service.url=…` on both `verify` commands. Hand-merge instead — adopt the template change, re-inject these. The template's GitHub Packages→JFrog publish migration additionally requires hand-aligning `.mvn/settings.xml`, which the sync tooling does not flag as eligible. (`renovate.json` used to carry a pandoc-specific `customManagers` block too; since the switch to `pandoc-service.api-version` it matches the template.)

- **pandoc-service compatibility is tracked by API version, not release version.** `versions.properties` holds `pandoc-service.api-version=<int>` and `PandocStatusProvider` compares it with the `apiVersion` reported by the service's `GET /version`; bump the property only when the service's API contract version changes (see `API_VERSION` in pandoc-service). The CI service-container image pin in `maven-build.yml` is updated by renovate independently.

- **Base/cross-cutting code is not in this repo.** `ch.sbb.polarion.extension.generic` is the parent project providing reusable infrastructure for all org Polarion plugins — settings framework, REST base classes, security (`@Secured`), OSGi helpers, servlets. Before implementing anything cross-cutting, check whether it already exists in `generic`.

- **After any code change, delete `<polarion_home>/data/workspace/.config` before restarting Polarion** — otherwise the changes are not picked up.

- **Pre-commit hooks reject org-internal identifiers as secrets.** The `sensitive-data-leak-*` and gitleaks hooks fail on internal URLs, UE numbers, and DEV ticket numbers — so a commit can be blocked by something that isn't an obvious secret. Run `pre-commit run -a` after implementing and fix any flags before pushing.

- **`pre-commit run -a` fails on `check-yaml`, and it is not yours.** `.github/workflows/maven-build.yml` declares `ports: [9082:9082]`; unquoted inside a flow sequence that is a plain scalar containing a colon, which PyYAML refuses to parse. GitHub Actions reads it fine. Quoting it is not enough on its own - `yamlfix` strips the quotes back off unless `.yamlfix.toml` sets `preserve_quotes`. The workflow files belong to DevOps, so leave both alone and ignore that one red hook. pdf-exporter has the identical problem with `ports: [9080:9080]`.
