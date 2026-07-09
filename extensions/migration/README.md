# AEM Groovy Console - Migration Extension

Deployment migration extension for the AEM Groovy Console, replacing the deprecated
[AEM Easy Content Upgrade (AECU)](https://github.com/valtech/aem-easy-content-upgrade) project.

Groovy migration scripts are deployed via content package below `/conf/groovyconsole/scripts/migration` and
executed with **checksum-based run-once semantics**: a script runs when it is new, its content changed or its
last execution was not successful. Scripts execute in deterministic alphanumeric path order with fail-fast
behavior, and a light run history is kept below `/var/groovyconsole/migration`.

## Installation (opt-in)

Install `aem-groovy-console-migration-all` **on top of** the Groovy Console (`aem-groovy-console-all`,
installed separately). The console itself has no dependency on this extension. The extension ships its own
service user (`aem-groovy-console-migration-service`) and OSGi configuration.

## Writing migration scripts

Deploy `.groovy` files (typically via a content package) below `/conf/groovyconsole/scripts/migration`.
Scripts are regular Groovy Console scripts with all console bindings (`resourceResolver`, `session`, etc.)
available, executed by the extension's service user. They are discovered recursively and executed in
alphanumeric path order, so a numeric prefix convention keeps the order explicit:

```
/conf/groovyconsole/scripts/migration/
    2025/
        001-activate-new-templates.groovy
        002-cleanup-legacy-paths.author.groovy
    003-rebuild-index.always.groovy
```

File name tokens between the script name and the `.groovy` extension modify execution:

- `author` / `publish` — only execute on instances with a matching Sling run mode (can be disabled via the
  `MigrationServiceProperties` OSGi configuration).
- `always` — execute on **every** migration trigger instead of run-once (e.g. `script.always.groovy`).

A script is **pending** (will run on the next trigger) when it has never been executed, its content checksum
changed, or its last execution was not successful. A run executes all pending scripts and **stops at the first
failure**: remaining scripts are reported `SKIPPED` and stay pending, so the run can be retried after fixing
the failing script.

Uncommitted repository changes of a successful script are committed automatically when the script finishes.
When a script fails, its uncommitted changes are **rolled back** before the failure is recorded; changes the
script already committed itself (e.g. an explicit `session.save()` halfway) are not rolled back.

## Triggering

- **HTTP API** (CI/CD): `POST /bin/groovyconsole/migration` — synchronous by default, `async=true` returns a
  `runId` for polling via `GET ?runId=...`, `dryRun=true` previews without executing. `path=...` scopes the run
  to a single script or folder instead of the configured scripts base path; `data=...` (JSON or plain string) is
  made available to every script in the run as the `data` binding variable. `GET ?registry=true` / `?pending=true`
  expose the per-script state. Returns `409 Conflict` while a run is in progress.
- **Resource listener** (opt-in, disabled by default): enqueues a run automatically when migration scripts
  are added/changed, debounced. Enable via the `MigrationScriptListener` OSGi configuration.
- **JMX** (e.g. JConsole, or a scripted JMX client): `be.orbinson.aem.groovyconsole:type=Migration` exposes
  `run()`, `run(path)` and `run(path, data)` (synchronous, same semantics as the HTTP API above), plus
  `isRunning()`, `getPendingScripts()` and `getRuns(count)`. Mirrors `AecuServiceMBean`, adapted to this
  service's always-run-once-and-fail-fast model (there is no history-bypassing execute mode).
- **Standalone history page**: `/apps/groovyconsole/migrations.html` shows the run history and per-script
  registry in table format. On AEM the page is also linked from the Tools console.

### HTTP API examples

```shell
# run all pending migration scripts and wait for the result
curl -u admin:admin -X POST http://localhost:4502/bin/groovyconsole/migration

# fire-and-poll from a deployment pipeline
runId=$(curl -su admin:admin -X POST "http://localhost:4502/bin/groovyconsole/migration?async=true" | jq -r .runId)
curl -su admin:admin "http://localhost:4502/bin/groovyconsole/migration?runId=$runId"

# preview which scripts would run
curl -u admin:admin -X POST "http://localhost:4502/bin/groovyconsole/migration?dryRun=true"

# run a single script, or everything below a specific folder, instead of the full base path
curl -u admin:admin -X POST --data-urlencode "path=/conf/groovyconsole/scripts/migration/2025/001-activate-new-templates.groovy" \
    http://localhost:4502/bin/groovyconsole/migration
curl -u admin:admin -X POST --data-urlencode "path=/conf/groovyconsole/scripts/migration/2025" \
    http://localhost:4502/bin/groovyconsole/migration

# pass data to the scripts in the run; available to scripts as the "data" binding (parsed as JSON when possible)
curl -u admin:admin -X POST --data-urlencode 'data={"tenant":"acme"}' http://localhost:4502/bin/groovyconsole/migration

# inspect the per-script registry state
curl -u admin:admin "http://localhost:4502/bin/groovyconsole/migration?registry=true"
```

Triggering requires the `admin` user or membership in one of the groups configured via
`allowedMigrationGroups` (see below).

## Configuration

`be.orbinson.aem.groovy.console.migration.impl.DefaultMigrationService`:

| Property                 | Default                                | Description                                                        |
|--------------------------|----------------------------------------|--------------------------------------------------------------------|
| `scriptsBasePath`        | `/conf/groovyconsole/scripts/migration`| JCR path containing the migration scripts                          |
| `allowedMigrationGroups` | *(empty)*                              | Groups allowed to trigger runs (admin always allowed)              |
| `staleLockMillis`        | `1800000`                              | Time after which an in-progress run lock is considered stale       |
| `maxRunHistory`          | `50`                                   | Number of runs kept in the history; older runs are pruned          |
| `maxOutputChars`         | `4096`                                 | Script output characters stored per result (full output in audit)  |
| `runModeFilterEnabled`   | `true`                                 | Honor `author`/`publish` file name tokens                          |

`be.orbinson.aem.groovy.console.migration.impl.MigrationScriptListener`:

| Property         | Default | Description                                                       |
|------------------|---------|-------------------------------------------------------------------|
| `enabled`        | `false` | Automatically enqueue a run when migration scripts change         |
| `debounceMillis` | `3000`  | Coalesce bursts of change events into a single run                |

When overriding `scriptsBasePath`, the listener's `resource.paths` component property must be overridden
accordingly via OSGi configuration.

## Persistence model

- `/var/groovyconsole/migration/registry/*` — one entry per known script (checksum, last status, last run date).
- `/var/groovyconsole/migration/runs/<runId>` — aggregate run result with per-script results, pruned to
  `maxRunHistory`.
- Script executions also appear in the console's regular audit history with their full output.

## Module layout

`api` (exported interfaces), `bundle` (implementation), `ui.frontend` (Lit + Spectrum Web Components UI built
with Vite into `ui.apps` under the migration-owned `/apps/groovyconsole-migration/spa` path — see
`ui.frontend/src/console-shim` for the small self-contained API-client/config/persistence helpers it needs),
`ui.apps` (SPA assets + AEM Tools navigation overlay), `ui.config` (service user, repoinit, ordered job queue),
`ui.content` (the `/conf/groovyconsole/scripts/migration` folder) and `all` (the container package).
