# AEM Groovy Console - Migration Extension

Deployment migration extension for the AEM Groovy Console, replacing the deprecated
[AEM Easy Content Upgrade (AECU)](https://github.com/valtech/aem-easy-content-upgrade) project.

Groovy migration scripts are deployed via content package below an immutable `/apps/groovyconsole-migration-scripts`
path and/or the mutable `/conf/groovyconsole/scripts/migration` path, and executed with **checksum-based run-once
semantics**: a script runs when it is new, its content changed or its last execution was not successful. Scripts
execute in deterministic alphanumeric path order with fail-fast behavior, and a light run history is kept below
`/var/groovyconsole/migration`.

Works on **AEM 6.5 on-premises, AMS and AEM as a Cloud Service** as well as plain Apache Sling — see
[Cloud vs on-premises](#cloud-vs-on-premises) for how automatic execution differs per environment.

## Installation (opt-in)

Install `aem-groovy-console-migration-all` **on top of** the Groovy Console (`aem-groovy-console-all`,
installed separately). The console itself has no dependency on this extension. The extension ships its own
service user (`aem-groovy-console-migration-service`) and OSGi configuration.

## Writing migration scripts

Deploy `.groovy` files (typically via a content package) below one of the configured scripts base paths. Two
paths are searched by default (both optional, searched in order, missing paths skipped):

- **`/apps/groovyconsole-migration-scripts`** — *immutable*, recommended on AEM as a Cloud Service. Scripts
  ship inside the code image (present on both author and publish, read-only at runtime) and are picked up
  automatically by the [cloud startup hook](#cloud-vs-on-premises). Deploy them here from your project's own
  content package.
- **`/conf/groovyconsole/scripts/migration`** — *mutable*, suited to authored or ad-hoc scripts.

Configure a different set (or a single path) via `scriptsBasePaths` on the `MigrationService` OSGi
configuration. Scripts are regular Groovy Console scripts with all console bindings (`resourceResolver`,
`session`, etc.) available, executed by the extension's service user. They are discovered recursively and
executed in alphanumeric path order across all base paths (`/apps` sorts before `/conf`), so a numeric prefix
convention keeps the order explicit:

```
/apps/groovyconsole-migration-scripts/
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

- **Startup hook** (automatic, cloud by default): on bundle activation the extension detects an AEM as a Cloud
  Service instance (composite node store) and enqueues a run of the pending scripts once the instance is ready.
  Controlled by `autoRunOnStartup` (`cloudOnly` default / `always` / `never`) on the `MigrationStartupHook` OSGi
  configuration — see [Cloud vs on-premises](#cloud-vs-on-premises).
- **HTTP API** (CI/CD): `POST /bin/groovyconsole/migration` — synchronous by default, `async=true` returns a
  `runId` for polling via `GET ?runId=...`, `dryRun=true` previews without executing. `path=...` scopes the run
  to a single script or folder instead of the configured scripts base path; `data=...` (JSON or plain string) is
  made available to every script in the run as the `data` binding variable. `GET ?registry=true` / `?pending=true`
  expose the per-script state. Returns `409 Conflict` while a run is in progress.
- **Resource listener** (opt-in, disabled by default): enqueues a run automatically when migration scripts
  are added/changed under either default base path, debounced. Enable via the `MigrationScriptListener` OSGi
  configuration. Note that immutable `/apps` changes on AEMaaCS are applied by a container swap and do not fire
  resource events — the startup hook covers auto-run there instead.
- **JMX** (e.g. JConsole, or a scripted JMX client): `be.orbinson.aem.groovyconsole:type=Migration` exposes
  `run()`, `run(path)` and `run(path, data)` (synchronous, same semantics as the HTTP API above), plus
  `isRunning()`, `getPendingScripts()` and `getRuns(count)`. Mirrors `AecuServiceMBean`, adapted to this
  service's always-run-once-and-fail-fast model (there is no history-bypassing execute mode).
- **Modern console UI**: a "Migrations" panel in the console's activity rail (registered via the
  `ConsoleUiExtensionProvider` SPI) and a standalone history page at `/apps/groovyconsole/migrations.html`
  with the run history and per-script registry in table format. On AEM the history page is also linked from
  the Tools console.

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

## Health checks

Two Apache Felix Health Checks are registered under the `migration` tag (mirroring AECU's
`LastRunHealthCheck`/`SelfCheckHealthCheck`), queryable as a group via `GET /system/health?tags=migration`
(or any other Felix HC-compatible client, e.g. the Web Console "Health Check" tab or the
`org.apache.felix.hc.core.HealthCheckExecutorMBean` JMX MBean):

- **Last Run** (tag `migration-last-run`) — CRITICAL if the most recent run failed, WARN if a run is still in
  progress, OK if the most recent run succeeded or none has run yet.
- **Self Check** — CRITICAL if the extension's service user cannot log in or its repository root
  (`/var/groovyconsole/migration`) is unreachable; independent of any particular run's outcome.

## Configuration

`be.orbinson.aem.groovy.console.migration.impl.DefaultMigrationService`:

| Property                 | Default                                     | Description                                                        |
|--------------------------|---------------------------------------------|--------------------------------------------------------------------|
| `scriptsBasePaths`       | `/apps/groovyconsole-migration-scripts`, `/conf/groovyconsole/scripts/migration` | JCR paths containing the migration scripts, searched in order; missing paths skipped |
| `allowedMigrationGroups` | *(empty)*                                   | Groups allowed to trigger runs (admin always allowed)              |
| `staleLockMillis`        | `1800000`                                   | Time after which an in-progress run lock is considered stale       |
| `maxRunHistory`          | `50`                                        | Number of runs kept in the history; older runs are pruned          |
| `maxOutputChars`         | `4096`                                      | Script output characters stored per result (full output in audit)  |
| `runModeFilterEnabled`   | `true`                                      | Honor `author`/`publish` file name tokens                          |

`be.orbinson.aem.groovy.console.migration.impl.MigrationStartupHook`:

| Property                | Default     | Description                                                                                  |
|-------------------------|-------------|----------------------------------------------------------------------------------------------|
| `autoRunOnStartup`      | `cloudOnly` | When to auto-run on activation: `cloudOnly` (AEMaaCS composite node store), `always`, `never` |
| `bootDelayMillis`       | `10000`     | Delay after activation before the startup run, letting the instance settle                   |
| `readinessTimeoutMillis`| `300000`    | Max time to wait for the repository/service user to become available                         |

`be.orbinson.aem.groovy.console.migration.impl.MigrationScriptListener`:

| Property         | Default | Description                                                       |
|------------------|---------|-------------------------------------------------------------------|
| `enabled`        | `false` | Automatically enqueue a run when migration scripts change         |
| `debounceMillis` | `3000`  | Coalesce bursts of change events into a single run                |

When overriding `scriptsBasePaths`, the listener's `resource.paths` component property must be overridden
accordingly via OSGi configuration.

## Cloud vs on-premises

Migrations can run automatically on deployment in every supported environment; the available trigger differs,
so pick the row that matches yours:

| Environment                       | Recommended scripts path                | How migrations run on deploy                                                                  |
|-----------------------------------|-----------------------------------------|-----------------------------------------------------------------------------------------------|
| **AEM as a Cloud Service**        | `/apps/groovyconsole-migration-scripts` | **Startup hook** enqueues pending scripts automatically when the container starts. No pipeline step required. |
| **AEM 6.5 on-premises / AMS**     | either path (via content package)       | Enable the **resource listener** to run pending scripts when the package installs, or trigger the **HTTP API** / **JMX** from your deployment tooling. The startup hook stays inactive by default (`cloudOnly`). |
| **Apache Sling / local dev**      | either path                             | HTTP API, JMX, resource listener, or set `autoRunOnStartup=always` to run on every startup.    |

Scripts under `/apps` ship inside the immutable code image (present on author and publish, read-only at
runtime); scripts under `/conf` are mutable content, deployed to author only. Execution state always lives
under mutable `/var/groovyconsole/migration`. Set `autoRunOnStartup=always` to also auto-run on premises, or
`never` to rely solely on the listener / HTTP API / JMX.

On a horizontally scaled publish tier each pod runs the migrations against its own repository (each pod's
`/var` state is independent); the per-instance run lock in `/var/groovyconsole/migration` prevents concurrent
runs within an instance. Use the [health checks](#health-checks) (tag `migration`) to gate a cloud pipeline on
migration success.

## Persistence model

- `/var/groovyconsole/migration/registry/*` — one entry per known script (checksum, last status, last run date).
- `/var/groovyconsole/migration/runs/<runId>` — aggregate run result with per-script results, pruned to
  `maxRunHistory`.
- Script executions also appear in the console's regular audit history with their full output.

## Module layout

Follows the reports extension methodology: `api` (exported interfaces), `bundle` (implementation),
`ui.frontend` (Lit + Spectrum Web Components UI built with Vite into `ui.apps` under the migration-owned
`/apps/groovyconsole-migration/spa` path, sharing console infrastructure via the `@console` alias), `ui.apps`
(SPA assets + AEM Tools navigation overlay), `ui.config` (service user, repoinit, ordered job queue),
`ui.content` (the mutable `/conf/groovyconsole/scripts/migration` folder) and `all` (the container package).
Migration scripts destined for the immutable `/apps/groovyconsole-migration-scripts` path are deployed by the
customer's own content package, not by this extension.
