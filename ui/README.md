# DOCX Exporter UI

A React + Vite single-page app on [react-sbb-polarion](https://github.com/grigoriev/react-sbb-polarion)
(RSP), served from the `docx-exporter-app` webapp.

It replaced the JSP administration UI page by page, and now serves all of it: the legacy
`docx-exporter-admin` webapp is gone, its menu icons moved into this one.

## Feature routing

There is one `index.html` / bundle. The page to render is chosen from the `feature` query parameter:

- `/` (no param) renders a development landing page listing every feature.
- `/?feature=about` - About (RSP's shared `About`).
- `/?feature=disclaimer` - Usage Disclaimer. The only page whose article is **not** a REST call:
  generic serves `/readme` and `/user-guide` but has no disclaimer endpoint, so `disclaimer.html` is
  read as a static file from this app's own webapp, where markdown2html writes it during the build.
- `/?feature=user-guide` - User Guide (RSP's shared `UserGuide`).
- `/?feature=filename` - Filename template (RSP's `CodeEditor` with the Velocity grammar over the
  single `filename-template` setting; the Default button loads the built-in template into the editor).
- `/?feature=style-package` - Style Package: everything one export is driven by. The three "Custom ..."
  switches (orientation, paper size, image density) are nullable here and not in pdf-exporter: unticking
  one stores null, meaning "take what the reference template says".
- `/?feature=templates` - Templates: the reference DOCX a style package converts against. The document is
  held as bytes only, and `POST /template/details` both describes it and validates it.
- `/?feature=style-package-weights` - Style Package Weights (RSP's shared `StylePackageWeights` over
  this extension's `settings/style-package/weights` endpoint; the list is shared with pdf-exporter).
- `/?feature=authorization` - Authorization (RSP's shared `AuthorizationSettings` over the
  `authorization` named setting). It reads generic's `/roles` endpoint, which is opt-in: the two roles
  controllers are registered in `PdfExporterRestApplication` for this page.

Features are declared in [`src/features.tsx`](src/features.tsx). Add a page component under
`src/pages/`, register it there, and it appears on the landing page automatically. The ids must stay
in sync with the `pageUrl`s in `src/main/resources/META-INF/hivemodule.xml` — a mismatch shows up as a
blank page in Polarion and no test catches it.

## Local development

No Polarion restart is needed to develop the UI:

```bash
cd ui
cp .env.local.template .env.local   # optional: VITE_BASE_URL / VITE_BEARER_TOKEN for real REST calls
npm install
npm run dev                          # http://localhost:5173/
```

REST calls are proxied to the Polarion instance in `VITE_BASE_URL`; a personal access token in
`VITE_BEARER_TOKEN` switches `useRemote` from the session `/internal` endpoints to the token `/api`
ones.

## Running the tests

**One command, locally and in CI: `npm run test:coverage:docker`.** It runs the full suite (behavior +
visual regression) plus the 80% istanbul coverage gate inside the pinned Playwright Docker image,
which is what the Maven `test` phase and the pre-commit hook execute. Docker must be running.

```bash
npm run test:coverage:docker   # the canonical run: full suite + coverage gate, in the pinned image
npm run test:coverage          # fast local loop: behavior only + the gate, no Docker, no pixels
npm run test:update:docker     # regenerate the committed reference PNGs after an intentional UI change
```

> Do **not** run `npm run test:coverage:full` directly outside a container. It is the inner command the
> Docker wrapper invokes; the visual suites detect that they are not in the reference environment and
> skip themselves, so a run there proves nothing about the screenshots.

## Formatting, linting & typechecking

```bash
npm run format          # Prettier: format every file in place
npm run format:check    # Prettier: check only (what pre-commit / CI runs)
npm run lint            # ESLint: report problems
npm run lint:fix        # ESLint: auto-fix what it can
npm run typecheck       # tsc --noEmit over src/ and test/
```

`typecheck` runs first in `npm run build`, so the Maven build fails on a type error rather than only the
IDE showing one. `tsconfig.json` covers `src` **and** `test`: a test is code, and while it was left out
two assertions in `useRemote.test.tsx` indexed an untyped mock's call tuple. The config files themselves
(`vite.config.js`, `vitest.config.ts`, `scripts/*.mjs`) are still outside the program.

The repo's pre-commit hooks run `format:check`, `lint` and the dockerized coverage suite on any change
under `ui/`. They are check-only and never modify your files.

## Production build

`npm run build` emits the bundle to `ui/dist/app` with base path
`/polarion/docx-exporter-app/ui/app/`. The Maven build (frontend-maven-plugin +
maven-resources-plugin) runs this automatically and copies the bundle into
`src/main/resources/webapp/docx-exporter-app/app`, where `AADSynchronizerAppServlet` serves it at
`/polarion/docx-exporter-app/ui/app/index.html`.
