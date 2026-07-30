# CLAUDE.md

## Gotchas

- **Two administration UIs at once.** The administration pages are being converted to React on
  [react-sbb-polarion](https://github.com/grigoriev/react-sbb-polarion) one at a time, so two webapps
  serve them side by side: `docx-exporter-app` (the Vite bundle in `ui/`, see [`ui/README.md`](ui/README.md))
  and the legacy `docx-exporter-admin` (the remaining JSP pages). `hivemodule.xml` carries a `pageUrl`
  per menu entry, which is what makes the split possible; the ids there must match `ui/src/features.tsx`.
  Converted so far: About, Usage Disclaimer, User Guide, Authorization, Style Package Weights. `docx-exporter-admin` is deleted
  once the last JSP page is gone.

- **The UI build comes from the generic parent**, activated by the presence of `ui/package.json` (its
  `vite-ui` profile): `npm ci` + `npm run build`, the bundle copied into `webapp/docx-exporter-app/`, and
  the JS suite in the Maven `test` phase. This pom adds nothing for it, but it does have to defend
  against it: the parent declares `frontend-maven-plugin` with a plugin-level
  `<workingDirectory>ui</workingDirectory>`, which Maven merges into this pom's own declaration of the
  same plugin. Each of the three product-JS executions therefore names `${project.basedir}` explicitly -
  without that, `npm run test` runs the Vitest browser suite in `ui/` instead of this project's mocha
  suite, and the product JS is never tested (it cost pdf-exporter a red CI to notice).

- **`mvn verify` silently skips the entire Pandoc conversion test suite.** `BasePandocTest` is `@SkipTestWhenParamNotSet` keyed on the `docxExporterImpl` system property, so a plain build reports green without ever running the real HTML→DOCX tests. To actually run them, use `mvn verify -P tests-with-pandoc-docker -Dpandoc.service.url=<url>` (the profile sets `docxExporterImpl=docker`) with a reachable [pandoc-service](https://github.com/SchweizerischeBundesbahnen/pandoc-service) container. This is what CI does.

- **Template-sync: never blind-copy `maven-build.yml` from `open-source-polarion-java-repo-template`.** It carries pandoc-specific config the template lacks, and a verbatim copy silently breaks CI: `maven-build.yml` has the `pandoc` service container plus `-P tests-with-pandoc-docker -Dpandoc.service.url=…` on both `verify` commands. Hand-merge instead — adopt the template change, re-inject these. The template's GitHub Packages→JFrog publish migration additionally requires hand-aligning `.mvn/settings.xml`, which the sync tooling does not flag as eligible. (`renovate.json` used to carry a pandoc-specific `customManagers` block too; since the switch to `pandoc-service.api-version` it matches the template.)

- **pandoc-service compatibility is tracked by API version, not release version.** `versions.properties` holds `pandoc-service.api-version=<int>` and `PandocStatusProvider` compares it with the `apiVersion` reported by the service's `GET /version`; bump the property only when the service's API contract version changes (see `API_VERSION` in pandoc-service). The CI service-container image pin in `maven-build.yml` is updated by renovate independently.

- **Base/cross-cutting code is not in this repo.** `ch.sbb.polarion.extension.generic` is the parent project providing reusable infrastructure for all org Polarion plugins — settings framework, REST base classes, security (`@Secured`), OSGi helpers, servlets. Before implementing anything cross-cutting, check whether it already exists in `generic`.

- **After any code change, delete `<polarion_home>/data/workspace/.config` before restarting Polarion** — otherwise the changes are not picked up.

- **Pre-commit hooks reject org-internal identifiers as secrets.** The `sensitive-data-leak-*` and gitleaks hooks fail on internal URLs, UE numbers, and DEV ticket numbers — so a commit can be blocked by something that isn't an obvious secret. Run `pre-commit run -a` after implementing and fix any flags before pushing.
