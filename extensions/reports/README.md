# Groovy Console Reports

A business-facing reporting extension for the AEM Groovy Console. Authors write an ordinary Groovy script that
returns tabular data; business users run it from a form, page through the result, export it and browse past
runs — without touching the console itself.

It is an optional add-on that ships as its own content package, `aem-groovy-console-reports-all`, installed
separately from (and on top of) the console.

## Installation

The reports extension is independent of the console package, so install it separately — **the Groovy Console must
be installed first**:

- **Manual**: download `aem-groovy-console-reports-all` from the
  [releases](https://github.com/orbinson/aem-groovy-console/releases) (or
  [Maven Central](https://search.maven.org/search?q=a:aem-groovy-console-reports-all)) and install the content
  package with [PackMgr](http://localhost:4502/crx/packmgr).
- **Maven / embedded**: depend on the package and embed it like the console's `aem-groovy-console-all` (see the
  root README's *Embedded package* section):

  ```xml
  <dependency>
    <groupId>be.orbinson.aem</groupId>
    <artifactId>aem-groovy-console-reports-all</artifactId>
    <version>${aem-groovy-console.version}</version>
    <type>zip</type>
  </dependency>
  ```

On plain Sling (no AEM), XLSX export additionally needs the POI bundles — see [Exports](#exports). The console
works without this package; installing it adds the reports feature.

## Contents

- [Installation](#installation)
- [Report definitions](#report-definitions)
- [Parameters](#parameters)
- [Writing the script](#writing-the-script)
- [Column types](#column-types)
- [Execution model](#execution-model)
- [Scheduling](#scheduling)
- [Exports](#exports)
- [Distribution](#distribution)
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

## Scheduling

A report can run **unattended on a cron schedule**. Enable it in the editor's *Schedule* section (or set a
`schedule` child node on the definition) with a Quartz-style cron expression and, optionally, fixed parameter
values used for the scheduled run (there is no interactive form). Each scheduled report maps to exactly one Sling
scheduled job on the topic `groovyconsole/reports/job`, keyed on the definition's node path, so schedules persist
across restarts and, in a cluster, fire on a single instance. Saving the report reconciles the job (create /
update / remove); deleting the report removes it. When the job fires the report runs through the normal
[execution model](#execution-model) and its configured [distributions](#distribution) are applied to the result.

The cron expression is the Sling scheduler's Quartz form — 6 or 7 whitespace-separated fields
(`seconds minutes hours day-of-month month day-of-week [year]`), e.g. `0 0 6 * * ?` for 06:00 daily. It is
validated when the report is saved; an invalid expression is rejected with a `400` before anything is persisted.
**Sub-minute schedules are not allowed**: the seconds field must be a fixed value (0–59), so a report cannot be
set to run every second.

### Code-deployed schedules

Reports authored in the UI live under `/conf`. Reports **deployed in code** are auto-discovered from the immutable
drop-zone **`/apps/groovyconsole-reports-definitions`**: on startup, and whenever that tree changes (e.g. a content
package install), their enabled schedules are registered and schedules for definitions that no longer exist are
removed. Ship your definitions there in their own content package (a sibling of the reports package's own
`/apps/groovyconsole-reports`, so neither package's install wipes the other). Because `/apps` cannot be written at
runtime through the repository's write servlets, definitions found here are trusted to run under the executor
service user (see below).

### Run-as identity

A scheduled run has no request user, so it needs an identity. There are two, by how the report was created:

- **Scheduled through the UI/API** — the report runs **as its author**, with that user's own permissions, so it
  sees exactly what they can see. The author is set server-side (`runAs`/`scheduledBy` in the request body are
  ignored): a user can only ever schedule a report to run **as themselves**, never as another user, so scheduling
  can never be used to gain access. This is enabled by a self-scoped impersonation grant made when the schedule is
  saved — a user may grant a system user impersonation over their *own* account, so no privileged
  user-administration is involved. If that author later can't be impersonated (grant removed, user disabled), the
  run fails closed with a clear error rather than running with the wrong rights.
- **Deployed in code** (a definition installed by a content package, with no `runAs`) — the report runs as the
  dedicated executor service user **`aem-groovy-console-reports-executor`**, which ships with `jcr:read` on
  `/content`. This is the identity for reports shipped and vetted by developers. Scope its ACLs to the trees your
  code-deployed reports need.

The executor is a separate identity from the minimal bookkeeping service user
(`aem-groovy-console-reports-service`, which only persists executions under `/var`), so execution rights and
bookkeeping rights stay cleanly separated. Because a report editor can also edit the executable Groovy, treat
write access to `/conf/groovyconsole/reports` as the trust boundary and restrict it accordingly (see
[Access control](#access-control)).

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

## Distribution

A report can **distribute** its result to one or more destinations. Each *distribution target* pairs a distributor
with an [export format](#exports), so any registered exporter (CSV, XLSX, …) can be delivered. Targets are
configured in the editor's *Distribution* section and stored on the definition; they are applied automatically
when a **scheduled** run finishes successfully, and on demand for any completed run via **Distribute now** in the
run view. A distribution failure is recorded on the execution (`distributionErrors`) but never fails the run
itself.

Distributors are discovered from registered
`be.orbinson.aem.groovy.console.reports.ReportDistributor` services (mirroring the exporter SPI), so a custom
distributor registered as an OSGi service surfaces automatically in the API and UI. Only distributors that report
themselves **available** (`ReportDistributor.isAvailable()`) are offered as a destination — the filesystem
distributor is available only when enabled, and the email distributor only when a mail service is bound — so
authors are never shown a destination that would fail. Two ship built in:

- **Email** (`email`) — attaches the rendered export and sends it via AEM's mail service (SMTP is configured on
  the platform, as for the console's own notifications). Config: `recipients` (comma/semicolon/space separated)
  and an optional `subject`. Inactive on plain Sling with no mail service. An optional **recipient-domain
  allowlist** bounds where reports may be sent, via
  `be.orbinson.aem.groovy.console.reports.impl.EmailReportDistributorConfig`:

  | Property                  | Default | Meaning                                                                        |
  |---------------------------|---------|--------------------------------------------------------------------------------|
  | `allowedRecipientDomains` | (empty) | Recipient domains reports may be sent to. Empty means any address is allowed.  |

  When set, every recipient's domain must match an entry or the whole distribution is rejected (recorded in
  `distributionErrors`); leave it empty to send anywhere.
- **Filesystem** (`filesystem`) — writes the rendered export to a directory on the local filesystem. Config:
  `directory` and an optional `filename` (defaults to `<report>-<timestamp>.<ext>`). **Disabled by default** —
  writing to disk is sensitive, so enable it deliberately via
  `be.orbinson.aem.groovy.console.reports.impl.FilesystemReportDistributorConfig`:

  | Property               | Default | Meaning                                                                     |
  |------------------------|---------|-----------------------------------------------------------------------------|
  | `enabled`              | `false` | Enable the filesystem distributor.                                          |
  | `allowedRootDirectory` | (empty) | Absolute path every target directory must resolve within. Required to use.  |

  Target directories and file names are resolved against `allowedRootDirectory` with a canonical-path check, so
  `..` segments or an absolute path that escapes the configured root are rejected.

## Access control

A report is a single inline Groovy script stored at `/conf/groovyconsole/reports/<name>`, and **all report
operations run with the requesting user's session**. Reports are intended to be **authored by developers /
administrators and run by business users**, so authoring and running are gated differently:

- **Running / viewing / exporting / deleting** a report needs only **JCR access** to the report node (read to
  run/view/export, delete access to remove). None of these are gated by the console's allowed groups, so business
  users with read-only permissions can run reports; the report executes with their own session, seeing only what
  they are allowed to see.
- **Creating / editing** a report — and the editor's "try out" preview, which runs an arbitrary posted script —
  additionally require the **console permission** (admin or a member of the console's `allowedGroups`, via
  `ConfigurationService.hasPermission`) **and** JCR write access to `/conf/groovyconsole/reports`. These are the
  only operations that introduce or execute unsaved report Groovy.

This closes the escalation where a user with only JCR write to the reports folder could plant arbitrary Groovy
for a higher-privileged user to run: introducing or changing report code now requires console-level trust, while
running stays open to business users because they can only execute vetted, developer-authored reports with their
own permissions. (Deleting a report removes code rather than introducing it, so it is governed by JCR alone.)

To let a group author reports, add it to the console's `allowedGroups` and grant it write on
`/conf/groovyconsole/reports`. To let a group only run reports, grant read on the report nodes.

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

Execution purging is configured separately on the purge service.
