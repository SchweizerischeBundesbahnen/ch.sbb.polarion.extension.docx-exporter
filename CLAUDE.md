# CLAUDE.md

## Gotchas

- **`mvn verify` silently skips the entire Pandoc conversion test suite.** `BasePandocTest` is `@SkipTestWhenParamNotSet` keyed on the `docxExporterImpl` system property, so a plain build reports green without ever running the real HTML→DOCX tests. To actually run them, use `mvn verify -P tests-with-pandoc-docker -Dpandoc.service.url=<url>` (the profile sets `docxExporterImpl=docker`) with a reachable [pandoc-service](https://github.com/SchweizerischeBundesbahnen/pandoc-service) container. This is what CI does.

- **Template-sync: never blind-copy `maven-build.yml` or `renovate.json` from `open-source-polarion-java-repo-template`.** Both carry pandoc-specific config the template lacks, and a verbatim copy silently breaks CI: `maven-build.yml` has the `pandoc` service container plus `-P tests-with-pandoc-docker -Dpandoc.service.url=…` on both `verify` commands; `renovate.json` adds a `customManagers` block tracking `pandoc-service.version`. Hand-merge instead — adopt the template change, re-inject these. The template's GitHub Packages→JFrog publish migration additionally requires hand-aligning `.mvn/settings.xml`, which the sync tooling does not flag as eligible.

- **Base/cross-cutting code is not in this repo.** `ch.sbb.polarion.extension.generic` is the parent project providing reusable infrastructure for all org Polarion plugins — settings framework, REST base classes, security (`@Secured`), OSGi helpers, servlets. Before implementing anything cross-cutting, check whether it already exists in `generic`.

- **After any code change, delete `<polarion_home>/data/workspace/.config` before restarting Polarion** — otherwise the changes are not picked up.

- **Pre-commit hooks reject org-internal identifiers as secrets.** The `sensitive-data-leak-*` and gitleaks hooks fail on internal URLs, UE numbers, and DEV ticket numbers — so a commit can be blocked by something that isn't an obvious secret. Run `pre-commit run -a` after implementing and fix any flags before pushing.
