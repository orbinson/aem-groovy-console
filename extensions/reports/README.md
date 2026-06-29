# Groovy Console Reports

A business-facing reporting extension for the AEM Groovy Console. Authors write an ordinary Groovy script that
returns tabular data; business users run it from a form, page through the result, export it and browse past
runs — without touching the console itself.

It ships as the `aem-groovy-console-reports-all` content package. **Install the Groovy Console first**, then this
package on top.

## Contents

- [Report definitions](#report-definitions)
- [Parameters](#parameters)
- [Writing the script](#writing-the-script)
- [Column types](#column-types)
- [Execution model](#execution-model)
- [Exports](#exports)
- [Access control](#access-control)
- [User interfaces](#user-interfaces)
- [Configuration](#configuration)

## Report definitions

A report is a single node under `/conf/groovyconsole/reports/<name>` holding a title, description, category, the
Groovy script (inline, or referencing a saved console script) and its typed parameters. Because the definition is
just a JCR node, it is created, edited and deleted through the normal resolver — JCR access control alone governs
who may do what (see [Access control](#access-control)).

## Parameters

Each parameter declares a `name`, optional `label`, a `type` and whether it is `required`. Parameter values are
coerced to the declared type and passed to the script as the `params` binding (`params.<name>`).

| Type      | Input rendered in the run form                | Coerced to        |
|-----------|-----------------------------------------------|-------------------|
| `STRING`  | text field                                    | `String`          |
| `NUMBER`  | number field                                  | `Long` / `Double` |
| `BOOLEAN` | checkbox                                       | `Boolean`         |
| `DATE`    | native date picker (`<input type="date">`)    | `Calendar`        |
| `SELECT`  | dropdown of the declared options              | `String`          |
| `PATH`    | repository path browser (page/asset/node)     | `String`          |

The editor's **try it out** panel renders the same typed inputs (including the date picker for `DATE`) so a
preview run behaves like a real run.

## Writing the script

The script runs through the Groovy Console, so every console binding, star import and the audit trail apply. Return
either a `ReportData` (typed columns) or a classic console `Table` (all-string columns).

```groovy
def data = report.data()

data.column('Page', ReportColumnType.LINK)
data.column('Title')                          // STRING (the default)
data.column('Views', ReportColumnType.NUMBER)
data.column('Modified', ReportColumnType.DATE)

pages.each { page ->
    data.row(
        [text: page.title, href: page.path + '.html'],  // LINK cell
        page.title,
        page.views,
        page.lastModified
    )
}

data
```

## Column types

The column type drives both how a cell is rendered in the UI and how it is written to typed exports (e.g. numeric
and date cells in XLSX). Available types: `STRING`, `NUMBER`, `DATE`, `BOOLEAN`, `LINK`.

### `LINK` vs `STRING`

A `STRING` cell is plain text. A `LINK` cell is a **clickable hyperlink** with a separate label and target:

- **Cell value** — a map `[text: '...', href: '...']`. A bare string is accepted and used as both the label and
  the target.
- **In the UI** — rendered as an `<a target="_blank" rel="noopener">`. Only `http`/`https`/`mailto` (and relative)
  hrefs are linked; any other scheme (e.g. `javascript:`) falls back to plain text, so report data can never
  inject a dangerous link.
- **In exports** — flattened to `"text (href)"` (or just the href when there is no distinct label).

Use `LINK` whenever a cell should navigate somewhere — a page, an asset, an external system:

```groovy
data.column('Asset', ReportColumnType.LINK)
data.row([text: asset.name, href: '/assets.html' + asset.path])
```

If you used `STRING` instead you would only get the raw text — the path would not be clickable and you could not
show a friendly label distinct from the URL.

## Execution model

Running a report is **asynchronous**. The run endpoint creates an execution node in `RUNNING` state and returns
immediately; the script then runs in the background under a detached copy of the requesting user's resolver, and
the execution flips to `SUCCESS` (or `FAILED`, capturing the stack trace) when it finishes. The UI polls the
execution until it settles.

The complete result is persisted as a gzipped JSON binary (`jcr:data`) under
`/var/groovyconsole/reports/executions`. **Every row is stored** — the UI paginates and exports from the persisted
result without re-running the script. Old executions are purged on a configurable schedule.

## Exports

Export formats are discovered from registered `be.orbinson.aem.groovy.console.reports.ReportExporter` services;
registering a new one automatically surfaces it in the API and UI.

- **CSV** ships built in. It is RFC 4180 compliant, writes a UTF-8 BOM (so Excel detects the encoding), neutralizes
  spreadsheet formula injection, and is locale-aware: locales whose decimal separator is a comma (nl, de, fr, …)
  get a `;` field delimiter so Excel still splits into columns, others get `,`. An exporter opts into the request
  locale by implementing `LocaleAwareReportExporter`.
- **XLSX** is provided by the `exporter-xlsx` bundle, which wires to AEM's built-in `com.adobe.granite.poi`
  bundle. On plain Sling, install the Apache ServiceMix POI bundles
  (`org.apache.servicemix.bundles:org.apache.servicemix.bundles.poi:5.2.3_1`, `org.apache.logging.log4j:log4j-api`,
  `org.apache.commons:commons-compress`) to enable it — see `it.tests/src/main/features/groovyconsole-reports.json`.
  Without a POI provider, CSV keeps working and XLSX is simply not offered.

A column can be excluded from exports (UI-only) by declaring it with `exported = false`:
`data.column('Edit', ReportColumnType.LINK, false)`.

## Access control

A report is a single inline Groovy script stored at `/conf/groovyconsole/reports/<name>`, and **all report
operations run with the requesting user's session** — there are no application-level groups, JCR access control
alone governs everything:

- **read** access to a report node → view / run / export it
- **write** access → create / edit / delete it

To stop a group from creating reports, deny write on `/conf/groovyconsole/reports`. Because an inline script is
arbitrary Groovy, write access to a report is equivalent to script-execution rights — grant it accordingly.

## User interfaces

Both UIs are built from `extensions/reports/ui.frontend` (Lit + Spectrum + Monaco, Vite). It is a self-contained
build that shares console infrastructure (API client, the Monaco/Groovy editor setup, config, state) from the core
`ui.frontend` via a `@console` path alias, and deploys its assets under the reports-owned JCR path
`/apps/groovyconsole-reports/spa` (the core console owns `/apps/groovyconsole`).

- **Business UI** at `/apps/groovyconsole/reports.html` — a report catalogue with search and categories; a run
  view with the parameter form, paginated result table, exports and execution history; and an editor view (Monaco
  Groovy editor + parameters) for users with write access. The page shell is served by a servlet in the reports
  bundle, so it exists only when the extension is installed.
- **Developer panel** — a Reports drawer in the modern console's left rail, contributed through the
  `ConsoleUiExtensionProvider` mechanism. The console dynamically imports the panel module the provider announces;
  without the extension installed the console carries no reports code paths at all.

## Configuration

`be.orbinson.aem.groovy.console.reports.impl.DefaultReportsConfigurationService`:

| Property           | Default | Meaning                                                          |
|--------------------|---------|------------------------------------------------------------------|
| `defaultPageSize`  | 50      | UI result page size when a report does not declare its own.      |
| `maxOutputLength`  | 100000  | Max characters of captured script output persisted per execution. |

Execution purging is configured separately on the purge service.
