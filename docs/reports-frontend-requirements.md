# Groovy Console Reports — Frontend Requirements & API Contract

Requirements and API contract for the reports UI, built with [Adobe Spectrum Web Components](https://opensource.adobe.com/spectrum-web-components/) (SWC).
The backend lives in the `extensions/reports/` modules of this repository and is deployed as the separate
`aem-groovy-console-reports-all` content package. The UI works on **both AEM and plain Sling** —
no Granite/Coral UI, no Composum clientlibs; plain static ES modules + CSS.

**Implementation status**: the UI described here is implemented in `ui.frontend` as part of the modern console SPA:

* **Business UI** — second Vite entry (`reports.html` → `src/reports-main.ts`, components under
  `src/components/reports/`), served at `/apps/groovyconsole/reports.html` with hash routing
  (`#/`, `#/report/<name>`, `#/report/<name>/edit`, `#/new`). The page shell is rendered by
  `ReportsPageServlet` in the reports bundle (a path-bound servlet, not JCR content), so it ships entirely
  with the reports extension and survives console package reinstalls. Selectors on `/apps/groovyconsole`
  remain reserved for the classic/modern console switch.
* **Developer UI** — the `gc-reports` drawer (`src/extensions/reports/gc-reports.ts`) hooks into the console
  via the **UI extension mechanism**: the reports bundle registers a
  `be.orbinson.aem.groovy.console.api.ConsoleUiExtensionProvider` OSGi service announcing
  `assets/reports-panel.js`; `ConsoleRouter` puts the module URLs into the page config (`uiExtensions`); the
  console SPA dynamically imports them (`src/extensions/registry.ts`) and each module registers rail panels
  through `window.GroovyConsole.registerPanel({ id, title, element, iconHtml })`. Extension panels are
  self-contained custom elements (shadow DOM) and interact with the console only via DOM events:
  `gc-set-script` (`{ script, message? }`) and `gc-toast` (`{ message, variant? }`). The console is fully
  functional without any providers — when this code moves to its own repository, only the provider service
  and the module assets move with it.
* **API client** — `src/api/reports-api.ts` + `src/api/reports-types.ts` implement the contract below; the JSON
  shapes are produced by the servlets in
  `extensions/reports/bundle/src/main/groovy/be/orbinson/aem/groovy/console/reports/servlets/`.

---

## 1. Concepts

- **Report definition** — a named, business-facing report backed by a Groovy script, stored at
  `/conf/groovyconsole/reports/<name>`. Declares typed **parameters** rendered as a form before running.
- **Execution** — one run of a report. The script runs **asynchronously** server-side: the execute POST returns
  immediately with a `RUNNING` execution, and the client polls `execution.json` until it is `SUCCESS`/`FAILED`.
  The full tabular result is persisted; re-viewing, paginating and exporting never re-run the script.
- **Result** — typed columns (`STRING|NUMBER|DATE|BOOLEAN|LINK`) + rows, read page by page.
- **Export formats** — **API-driven**: the set of formats is whatever exporters are registered on the server
  (csv always; xlsx when a POI provider is present). **Never hardcode formats — render what the API returns.**
- **Permissions** — governed entirely by JCR access control on `/conf/groovyconsole/reports` (read = view/run,
  write = create/edit/delete); there are no application-level access groups. The list response carries `canManage`
  (the user may create reports) and each report carries `canEdit` (the user may edit/delete it). A report that is
  returned at all is runnable. Use these flags to show/hide actions; the server enforces JCR permissions regardless
  (403/404).

## 2. Views

Hash-based routing, single page: `#/` (list), `#/report/<name>` (run), `#/report/<name>/edit` (editor).

### 2.1 Report list (`#/`)
- `sp-table` (or cards) of reports from `GET /bin/groovyconsole/reports.json`, grouped or filterable by
  `category`; show `title` + `description`.
- "New report" button only when `canManage` is true → opens editor with a blank definition.
- Row click → run view. Edit affordance (e.g. `sp-action-menu`) only when that report's `canEdit` is true.

### 2.2 Run view (`#/report/<name>`)
- Load the definition (`GET ...reports.json?name=`), render the **parameter form** from `parameters`
  (sorted by `order`):

  | parameter `type` | input | submitted value |
  |---|---|---|
  | `STRING` | `sp-textfield` | string |
  | `NUMBER` | `sp-number-field` (or textfield) | decimal string, e.g. `"42.5"` |
  | `BOOLEAN` | `sp-checkbox` / `sp-switch` | `"true"` / `"false"` |
  | `DATE` | date input | `"2026-06-04"` or `"2026-06-04T10:15:30Z"` (ISO-8601, UTC) |
  | `SELECT` | `sp-picker` with `options` | one of `options` |
  | `PATH` | `sp-textfield` (path autocomplete optional) | repository path string |

  Pre-fill `defaultValue`; mark `required` fields (server returns 400 with a message when missing/invalid).
- **Run** button → `POST /bin/groovyconsole/reports/execute` returns immediately with a `RUNNING` execution.
  Poll `GET .../execution.json?executionId=<id>` until `status` is `SUCCESS`/`FAILED`; show `sp-progress-circle`
  and disable the form while polling. (Async means long reports never block the request or hit HTTP timeouts.)
- On success render the result table:
  - `GET /bin/groovyconsole/reports/result.json?executionId=&page=1` (page size defaults server-side to the
    report's `pageSize`).
  - `sp-table` with `scroller` for the rows of the current page; **pagination controls** from
    `page`/`totalPages`/`nextPage`/`previousPage` (prev/next + page indicator + page-size picker).
  - **Cell rendering by column type**: `LINK` → `<a href="{href}">{text}</a>`; `DATE` → locale-formatted
    (value is an ISO-8601 UTC string); `NUMBER` → right-aligned; `BOOLEAN` → checkmark/dash; `STRING` → text.
  - Show `truncated` warning badge when the execution was row-capped, and `output` (script println output)
    in a collapsible panel when non-empty.
- **Export buttons — one per entry in the definition's `exportFormats` (or `GET .../formats.json`)**. Each is
  a plain download link: `GET /bin/groovyconsole/reports/export?executionId=<id>&format=<format>`
  (server sets `Content-Disposition`).
- **Execution history**: `GET .../executions.json?name=` → `sp-picker`/list of prior runs (status, user,
  startedAt, rowCount). Selecting one loads its result via `result.json` without re-running. Delete affordance
  (when `canEdit`) → `DELETE .../executions?executionId=`.
- FAILED executions: show `exceptionStackTrace` in a collapsible error panel (`sp-toast` negative + detail).

### 2.3 Editor view (`#/report/<name>/edit`)
- Metadata: `title`, `description`, `category`, `pageSize`.
- Script: **CodeMirror 6** with the legacy Groovy mode (`@codemirror/legacy-modes`). Every report has a single
  inline Groovy `script`.
- Parameters editor: orderable rows with name / label / type / defaultValue / required / options (options only
  for SELECT).
- Access control is via JCR permissions, not an editor field; there are no view/edit group inputs.
- Save → `POST /bin/groovyconsole/reports` with the full definition JSON. Delete → `DELETE ...?name=`
  behind an `sp-alert-dialog` confirmation.

## 3. HTTP API contract

All endpoints are under `/bin/groovyconsole/reports`. JSON in/out (`application/json; charset=UTF-8`) except
export (binary download). Errors: appropriate HTTP status + body `{"error": "<message>", "status": <code>}`.
- `400` validation error (missing/invalid parameter, unknown format, bad body)
- `401/403` not authenticated / not allowed
- `404` unknown report / execution / missing result

### 3.1 `GET /bin/groovyconsole/reports.json` — list
```json
{
  "reports": [
    { "name": "sample-content-listing", "title": "Sample: Content listing",
      "description": "…", "category": "Samples", "canEdit": false }
  ],
  "canManage": false
}
```
Only reports the user has JCR read access to are returned; `canManage` = the user may create reports.

### 3.2 `GET /bin/groovyconsole/reports.json?name=<name>` — single definition
```json
{
  "name": "sample-content-listing",
  "title": "Sample: Content listing",
  "description": "…",
  "category": "Samples",
  "script": "…groovy…",
  "pageSize": 25,
  "parameters": [
    { "name": "path", "label": "Content path", "type": "PATH",
      "defaultValue": "/content", "required": true, "options": [], "order": 0 }
  ],
  "created": "2026-06-04T19:00:00.000Z",
  "createdBy": "admin",
  "lastModified": "2026-06-04T19:30:00.000Z",
  "lastModifiedBy": "admin",
  "canEdit": true,
  "exportFormats": [
    { "format": "csv",  "contentType": "text/csv", "fileExtension": "csv" },
    { "format": "xlsx", "contentType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "fileExtension": "xlsx" }
  ]
}
```

### 3.3 `POST /bin/groovyconsole/reports` — create/update
Request body: same shape as 3.2 minus the read-only fields (`created*`, `lastModified*`, `can*`,
`exportFormats`). `name` is required, URL-safe (`[A-Za-z0-9_-]+`), and immutable (it's the node name).
An inline `script` is required. Response: the saved definition (shape 3.2). The write uses the user's session,
so creating requires JCR write access to `/conf/groovyconsole/reports` and updating requires write access to the
report node (otherwise `403`).

### 3.4 `DELETE /bin/groovyconsole/reports?name=<name>`
Response: `{"deleted": "<name>"}`. Requires `canEdit`.

### 3.5 `POST /bin/groovyconsole/reports/execute` — run (asynchronous)
Request:
```json
{ "name": "sample-content-listing", "parameters": { "path": "/content" } }
```
All parameter values are sent as **strings** (the server coerces by declared type). The POST returns
immediately with a `RUNNING` execution (same shape as 3.6 / entries of 3.7); poll `execution.json` until
`status` is `SUCCESS`/`FAILED`. On a finished run:
```json
{
  "executionId": "sample-content-listing/2026/06/04/execution",
  "reportName": "sample-content-listing",
  "status": "SUCCESS",                 // "RUNNING" immediately after execute, then "SUCCESS" | "FAILED"
  "userId": "admin",
  "startedAt": "2026-06-04T20:00:00.000Z",
  "finishedAt": "2026-06-04T20:00:03.000Z",
  "durationMillis": 3000,
  "runningTime": "00:00:03.000",
  "rowCount": 42,
  "columnCount": 3,
  "truncated": false,
  "parameterValues": { "path": "/content" },
  "output": "…captured println output…",   // may be null
  "exceptionStackTrace": null               // set when status == FAILED
}
```
400 on parameter validation errors (message describes the offending parameter).

### 3.6 `GET /bin/groovyconsole/reports/execution.json?executionId=<id>`
Single execution, same shape as 3.5. Poll this after `execute` until `status` is no longer `RUNNING`.

### 3.7 `GET /bin/groovyconsole/reports/executions.json?name=<name>`
```json
{ "executions": [ { …shape 3.5…, newest first } ] }
```

### 3.8 `DELETE /bin/groovyconsole/reports/executions?executionId=<id>`
Response: `{"deleted": "<id>"}`. Requires `canEdit` on the report.

### 3.9 `GET /bin/groovyconsole/reports/result.json?executionId=<id>&page=<n>&pageSize=<n>`
Pages are **1-based**. `pageSize` optional (report's `pageSize`, else server default 50).
```json
{
  "columns": [
    { "name": "Name",  "type": "STRING" },
    { "name": "Count", "type": "NUMBER" },
    { "name": "Modified", "type": "DATE" },
    { "name": "Active", "type": "BOOLEAN" },
    { "name": "Page",  "type": "LINK" }
  ],
  "rows": [
    ["hi-1", 10, "2026-06-04T20:00:00.000Z", false, { "text": "row-1", "href": "/content/row-1" }]
  ],
  "page": 1,
  "pageSize": 25,
  "totalRows": 42,
  "totalPages": 2,
  "nextPage": 2,          // -1 when on the last page
  "previousPage": -1      // -1 when on the first page
}
```
Cell value types by column type: `STRING` → string|null, `NUMBER` → JSON number|null, `DATE` → ISO-8601 UTC
string|null, `BOOLEAN` → boolean|null, `LINK` → `{"text": string, "href": string}`|null.

### 3.10 `GET /bin/groovyconsole/reports/formats.json`
```json
{ "formats": [ { "format": "csv", "contentType": "text/csv", "fileExtension": "csv" }, … ] }
```
Drive export buttons from this (or the `exportFormats` echo on the definition). A missing POI provider on
Sling simply means xlsx is absent — no special-casing needed.

### 3.11 `GET /bin/groovyconsole/reports/export?executionId=<id>&format=<format>`
Binary download; `Content-Type` from the exporter, `Content-Disposition: attachment;
filename="<reportName>-<yyyy-MM-dd'T'HHmmss>.<ext>"`. Use a plain `<a download href=…>` or
`window.location` — no fetch needed. `400` for unknown formats.

## 4. Build & serving

- **Module**: a new `reports/ui.frontend` Maven module wrapping an npm + **vite** build (ESM output, no
  hashed filenames or emit a manifest). Output copied into a `reports/ui.apps` content package under
  `/apps/groovyconsole/reports/clientlibs/static/` (plain `nt:file`s — no Composum/Granite clientlib).
- **Page registration** (mirror the host console's pattern in
  `ui.apps/.../apps/groovyconsole/.content.xml`): a `sling:Folder` at `/apps/groovyconsole/reports` with
  `sling:resourceType="groovyconsole/reports/components/page"` and `sling:vanityPath="/groovyconsole/reports"`;
  an HTL `page.html` that renders the shell:
  ```html
  <!DOCTYPE html>
  <html><head>
    <meta charset="utf-8">
    <link rel="stylesheet" href="/apps/groovyconsole/reports/clientlibs/static/reports.css">
  </head><body>
    <sp-theme system="spectrum" color="light" scale="medium">
      <groovy-reports-app></groovy-reports-app>
    </sp-theme>
    <script type="module" src="/apps/groovyconsole/reports/clientlibs/static/reports.js"></script>
  </body></html>
  ```
- **AEM Tools nav entry** (optional, separate `ui.apps.aem`-style package): node under
  `/apps/cq/core/content/nav/tools/general/groovyconsolereports` with `href=/apps/groovyconsole/reports.html`
  (mirror `ui.apps.aem/.../nav/tools/general/groovyconsole/.content.xml`).

## 5. SWC packages & gotchas

- Import **individual** packages, never `@spectrum-web-components/bundle`:
  `theme` (mandatory `<sp-theme>` wrapper — components are unstyled without it), `table`, `button`,
  `textfield`, `number-field`, `checkbox`, `picker`, `menu`, `action-menu`, `dialog` / `alert-dialog`,
  `toast`, `progress-circle`, `field-label`, `divider`, `search` (list filter).
- Lit-based ES modules; vite handles them natively. Avoid registry conflicts: pin one SWC version across all
  packages.
- Editor: **CodeMirror 6** + `@codemirror/legacy-modes/mode/groovy` (ACE is awkward as ESM; CM6 tree-shakes).
- **CSP**: no inline `<script>`/`<style>` — SWC uses constructable stylesheets, so a strict
  `script-src 'self'; style-src 'self'` works. Keep all JS in external module files.
- **CSRF**:
  - AEM: POST/DELETE to `/bin/*` require the Granite CSRF token — fetch `/libs/granite/csrf/token.json` and
    send it as the `CSRF-Token` header. Feature-detect (404 on Sling → skip).
  - Sling: the referrer filter applies to POST — same-origin fetch sends the `Referer`/`Origin` header
    automatically; no extra work.
- **Auth**: the page relies on the existing session (AEM login / Sling auth). On 401 redirect to the
  platform login; on 403 show an `sp-toast` "not allowed".
- **Errors**: centralize fetch in a small `api.js`; non-2xx → parse `{error}` body and toast it
  (`sp-toast` variant `negative`).
- **Dispatcher (AEM 6.5)**: allow + cache `/apps/groovyconsole/reports/clientlibs/static/*` and allow
  `/bin/groovyconsole/reports*`; this is an authoring/tools surface, not a publish-facing page.

## 6. Backend notes the UI should know

- Execution is asynchronous (background thread); the execute POST returns a `RUNNING` execution and the client
  polls `execution.json`. A failing script comes back `FAILED` with the stack trace in `exceptionStackTrace`.
- Results are capped at `maxResultRows` (OSGi config, default 10000) → `truncated: true`.
- Executions are purged on a schedule (defaults: 30 days / 50 per report) — old `executionId`s can 404;
  handle gracefully by refreshing the history list.
- The sample report `sample-content-listing` ships in `extensions/reports/ui.content` and works on both platforms —
  useful as a dev fixture.
- Report script authoring contract (for editor help text): script gets `params` (typed map) and `report`
  bindings; return `report.data()` (typed columns, preferred) or a console `Table` (all strings). Column
  types require an explicit import (no star import is injected). Example:
  ```groovy
  import be.orbinson.aem.groovy.console.reports.data.ReportColumnType

  def data = report.data()
  data.column('Page', ReportColumnType.LINK)
  data.column('Modified', ReportColumnType.DATE)
  resourceResolver.getResource(params.path).listChildren().each { child ->
      data.row([text: child.name, href: child.path], child.resourceMetadata.modificationTime)
  }
  data
  ```
