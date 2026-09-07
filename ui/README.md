# DOCX Exporter UI

A React + Vite single-page app on [react-sbb-polarion](https://github.com/SchweizerischeBundesbahnen/react-sbb-polarion)
(RSP), served from the `docx-exporter-app` webapp.

It replaced the JSP administration UI page by page, and now serves all of it: the legacy
`docx-exporter-admin` webapp is gone, its menu icons moved into this one.

## Feature routing

There is one `index.html` / bundle. The page to render is chosen from the `feature` query parameter:

- `/` (no param) renders a development landing page listing every feature.
- `/?feature=about` - About (RSP's shared `About`).
- `/?feature=disclaimer` - Usage Disclaimer. Reads the build-generated DISCLAIMER article from
  generic's `/disclaimer` endpoint, the same way About and User Guide read theirs. An empty response
  means the extension ships no disclaimer; the page then links to the online source.
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
  controllers are registered in `DocxExporterRestApplication` for this page.

Two further entries are **development harnesses**, marked by a label ending in ` (dev)`. Nothing in
Polarion points at them; they exist so the two runtime surfaces below can be driven in `vite dev`:
`/?feature=side-panel` and `/?feature=export-popup`. Both need a Polarion behind `VITE_BASE_URL`.

Features are declared in [`src/features.tsx`](src/features.tsx). Add a page component under
`src/pages/`, register it there, and it appears on the landing page automatically. The ids must stay
in sync with the `pageUrl`s in `src/main/resources/META-INF/hivemodule.xml` — a mismatch shows up as a
blank page in Polarion and no test catches it.

## The three entries

`index.html` is the administration SPA. The other two are ES modules that server-rendered markup imports
at runtime, each built to a **fixed** name because its importer names it by URL and cannot know the hash
Vite would emit:

| Entry                     | Emitted as               | Imported by                                       | Export it is called through |
| ------------------------- | ------------------------ | ------------------------------------------------- | --------------------------- |
| `src/sidepanel/mount.tsx` | `assets/side-panel.js`   | `webapp/docx-exporter/html/sidePanelContent.html` | `mountSidePanel(selector)`  |
| `src/popup/mount.tsx`     | `assets/export-popup.js` | `webapp/docx-exporter/js/starter.js`              | `openExportPopup()`         |

Both need `rollupOptions.preserveEntrySignatures: 'strict'` to keep that export, which a Vite app build
otherwise drops. Nothing in the Vitest suites sees the built files, so
[`scripts/check-runtime-entries.mjs`](scripts/check-runtime-entries.mjs) checks both after every build.

## The shared export model

Two surfaces export: the toolbar dialog and the Document Properties side panel. What they share is
[`src/export/`](src/export/) plus two services:

| Module                      | What it holds                                                                        |
| --------------------------- | ------------------------------------------------------------------------------------ |
| `export/exportForm.ts`      | A style package read into form state.                                                |
| `export/exportParams.ts`    | Form state turned into an export request.                                            |
| `export/exportData.ts`      | The REST reads each surface needs before it can be shown.                            |
| `export/validation.ts`      | The fields a user can get wrong.                                                     |
| `export/ExportFormView.tsx` | The form itself: every row, on both surfaces.                                        |
| `export/formRows.tsx`       | The two kinds of row it is built from, and the cell a control sits in.               |
| `export/export-form.css`    | How those rows and their columns are laid out; the toast styles a shadow root needs. |
| `export/reporting.ts`       | What the form tells the user, as toasts.                                             |
| `components/ToastHost.tsx`  | The one toast host that renders, wherever several are mounted.                       |
| `services/exportContext.ts` | Where the document is, read out of the Polarion location hash.                       |
| `services/conversion.ts`    | Submit a conversion job, poll it, download the result.                               |

There is no `documentType`: this extension exports Live Documents only. The legacy builder set one and
then never serialized it, and `ExportParams.java` has no such field, so the request does not carry one.

A setting the stored document expresses as "null means not overridden" is **two** fields in the form -
the switch and the value - so unticking a box does not throw away what was picked before ticking it
again. Orientation, paper size and image density work that way; pdf-exporter sends all three
unconditionally, which is why its `exportParams.ts` is not a drop-in here.

## The shared export form

The dialog and the side panel render the **same** form: `export/ExportFormView.tsx`. They were two copies of
it - the same rows, the same style package, the same request - laid out by hand, the dialog in two fixed flex
columns and the panel in one, each with its own label widths, its own way of hiding an optional field, and
its own way of reporting a failure. What is left per surface is the chrome: RSP's `Modal` with its footer, or
a `<fieldset>` in the properties pane with an "Export to DOCX" button of its own (passed in as `actions`).

Four things about that form are worth knowing. They are the same four as in
[pdf-exporter](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.pdf-exporter), whose
form this one is deliberately a sibling of - the two extensions are looked at side by side.

**It follows the room it has, not the surface it is on.** `.docx-export-form` is a query container
(`container-type: inline-size`), and a section of rows is one column below 620px of form width and two above
it. So the 360px properties pane gets one column, the dialog two, and a narrow window folds the dialog's form
without a rule of its own. That is also why `export-popup.css` **states** the dialog's width rather than
sizing it to its content: an element with inline-size containment contributes nothing to its parent's
max-content width, so a `width: max-content` dialog would collapse to one column at every window size.

**Every label starts at the same x.** A row is a three-track grid: a checkbox gutter, a label column, the
control. So the checkboxes line up down the whole form, the label texts line up beside them, and every
control lands on one more shared x, in both columns of the wide layout. The one exception is "Specific higher
level chapters", whose label is a sentence: widening the column to hold it would push every aligned control
halfway across the form, so that row lets its field follow the label instead (`.tight`).

**The order is the two-column order, and one column falls out of it.** Rows flow across a section before they
flow down, so the DOM order is the reading order in a pane and pairs up into columns in a dialog - with the
pair that belongs together ("cut empty chapters" / "cut empty Workitem attributes") side by side. Nothing is
assigned to a column. The sections are: the style package, the configurations the package points at, the page
setup, the render switches, comments rendering with its option under it, the workitem roles, the two typed
values, and the file name. The last four are one column at every width, and the file name takes a line of its
own.

**It reports through toasts.** A failed export, a conversion warning, a refused export and a successful one
are `toast.error` / `toast.warning` / `toast.success` - the same toasts every administration page raises,
through the same host (RSP's `Toaster`). The dialog's alert boxes inside the form and the panel's plain red
and orange text under its button are both gone. `export/reporting.ts` is the whole of it: a failure waits to
be dismissed, a warning outstays the 5s a "saved" toast gets, a success does not, and all three carry a close
button. The one message that stays in the form is the form that could not be loaded - a state rather than an
event, and a toast that came and went would leave a form that quietly does not work.

Toasts in a shadow root need two things an administration page does not. Their stylesheet has to be inside
the root, so `export/export-form.css` imports `sonner/dist/styles.css` and Vite inlines it; and only one host
may render at a time, which is `components/ToastHost.tsx` - `toast()` broadcasts to every mounted `Toaster`,
and the panel and the dialog are both on the page whenever a document is open in the editor, so both hosts
would show every message twice. The hosts take turns: the newest renders, the rest stand down. The dialog's
own host is rendered inside the dialog, a native `<dialog>` painting above anything outside it whatever its
z-index; the panel's is outside its `<fieldset>`, which is disabled while an export runs and would disable
the toast's close button with everything else.

An optional value field is hidden with `visibility` rather than removed: ticking a checkbox must not reflow
the rows around it. The three page setup dropdowns are the exception, and deliberately - unticked, the export
carries no value at all and the conversion takes what the reference template says, so an absent dropdown is
the state being described.

## The "Export to DOCX" dialog

`src/popup/` is the dialog the document editor's toolbar button opens. It imports
`assets/export-popup.js` and calls `openExportPopup()`.

The chrome is RSP's shared `Modal` - a native `<dialog>`, so the top layer, the backdrop and Escape come
for free. That replaced micromodal: `openExportPopup` appends a host to the page body, mounts into a
**shadow root** of it with RSP's stylesheet, the shared `src/export/export-form.css` and the dialog's own
`src/popup/export-popup.css` injected, and removes the host on close. Nothing is put on the page for it any more, which is why `starter.js` no longer injects
micromodal and six generic control stylesheets.

Because `<dialog>` is in the browser top layer, RSP's `SearchableSelect` option-list portal (a sibling of
the dialog inside the shadow root) painted underneath it. [`src/popup/dialogPortals.ts`](src/popup/dialogPortals.ts)
moves every `.sd-portal` into the dialog, which is also why the dialog may not clip its overflow.

## The Document Properties side panel

The "DOCX Exporter" pane of the document editor's Document Properties sidebar is `src/sidepanel/`, built
to `assets/side-panel.js`. `DocxExporterFormExtension` contributes nothing but the fragment that imports
it: an empty `#docx-exporter-panel` div plus a `<link>` to an empty `css/starter.css` whose `onload` fires
the import (an inline module `<script>` does not run inside a GWT-injected fragment).

It mounts into a **shadow root** of that div. The properties pane is one page shared by every extension's
panel, each possibly built against a different RSP version, so the isolation goes both ways:
`services/shadowMount.ts` (shared with the export dialog) injects RSP's stylesheet, a base-font rule
(nothing inside a shadow root inherits the page's font), the shared `export/export-form.css` and the panel's
own `side-panel.css` into the root, and none of it can leak out. Each of those two surface stylesheets is now
only the chrome around the shared form: the dialog's size and its overlay, the panel's fieldset, its loading
state and its button.

Everything the panel offers is read over REST from the endpoints the toolbar dialog has always used: the
suitable style packages, the child setting names, the link roles, the default file name, the document
language, the webhooks switch and the export permission. The server side used to substitute all of that
into the fragment's markup, which is why there is now one description of this form instead of two. The
trade is a short loading state, where the server-rendered panel arrived populated.

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
which is what the Maven `test` phase executes. Docker must be running.

### Two projects

`vitest.config.ts` declares two: **`browser`** runs the React app's suites in real Chromium, and
**`node`** runs the product injector scripts (`webapp/docx-exporter/js/`) in jsdom. Those scripts drive
the **top** frame, and browser mode runs each file in an iframe while keeping `top` for its own runner
page, so the injections would land in the runner's DOM. Name such a file `*.node.test.ts`; that suffix is
what routes it. The Docker runner mounts the repository root rather than `ui/` for the same reason - the
scripts under test sit outside `ui/`.

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
npm run format:check    # Prettier: check only
npm run lint            # ESLint: report problems
npm run lint:fix        # ESLint: auto-fix what it can
npm run typecheck       # tsc --noEmit over src/ and test/
```

`typecheck` runs first in `npm run build`, so the Maven build fails on a type error rather than only the
IDE showing one. `tsconfig.json` covers `src` **and** `test`: a test is code, and while it was left out
two assertions in `useRemote.test.tsx` indexed an untyped mock's call tuple. The config files themselves
(`vite.config.js`, `vitest.config.ts`, `scripts/*.mjs`) are still outside the program.

`format:check`, `lint` and the dockerized suite are wired into the repo's `.pre-commit-config.yaml` as
the `ui-prettier`, `ui-eslint` and `ui-test-coverage-docker` hooks, gated on any change under `ui/`.
They are check-only and never modify your files. All use `language: system`, so run `npm ci` in `ui/`
before `pre-commit run -a`. Without it they fail with an npm error rather than a lint finding.

The dockerized suite is the slow hook: it needs Docker running and adds 30-60s+ to a UI commit. That
is the price of catching a broken suite or a coverage drop before the push rather than in CI.

`typecheck` is not a hook. The Maven `test` phase gates it through the parent's `vite-ui` profile, and
it runs first in `npm run build`.

## Production build

`npm run build` typechecks, emits the three entries to `ui/dist/app` with base path
`/polarion/docx-exporter-app/ui/app/`, and then runs `scripts/check-runtime-entries.mjs` over the result.
The Maven build runs all of it automatically through the parent's `vite-ui` profile - this pom declares no
frontend plugin of its own - and copies the bundle into `src/main/resources/webapp/docx-exporter-app/app`,
where `DocxExporterAppServlet` serves it at `/polarion/docx-exporter-app/ui/app/index.html`.

> **Stop the dev server before running a Maven build.** The build runs `npm ci`, which starts by deleting
> `node_modules`, and on Windows that fails with `EPERM (-4048)` while `vite` holds files there - leaving
> `node_modules` half-deleted, so the dev server and the next build both break until `npm ci` is run
> again.
