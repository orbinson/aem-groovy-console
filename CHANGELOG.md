# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Modern UI** — a new IDE-style console built with the Monaco editor and Spectrum Web Components (`ui.frontend`
  module), with no AEM Granite/Coral dependency, so it runs on AEM and plain Sling. Resizable editor/output split,
  tabbed output (Log/Result/Table/Trace), slide-out drawers for History, Scheduled Jobs and Help, and clickable
  stack traces that jump to the offending line. It is now the default at `/groovyconsole` (the classic UI stays
  available at `/apps/groovyconsole.classic.html`).
- **Code assistance** in the modern editor — class, member and method completion (including Groovy GDK methods and
  OSGi service names), auto-import, hover documentation and live compile-error diagnostics, via new
  `/bin/groovyconsole/assist/*` endpoints.
- **Streaming script output** — run scripts asynchronously and stream their output while they execute
  (`POST /bin/groovyconsole/post.json?async=true` then poll `/bin/groovyconsole/stream.json`). Backwards compatible:
  existing clients that don't opt in behave exactly as before.
- **Extension mechanism** — optional features ship as their own content packages, kept out of the core `all` package.
  The console has no dependency on them and integrates only through public SPIs, including the new
  `ConsoleUiExtensionProvider`, which lets an extension contribute a panel to the modern UI.
- **Reports extension** — business-facing reports backed by Groovy scripts: named report definitions with typed
  parameters, asynchronous execution with persisted, paginated results, CSV/XLSX export, and a dedicated business UI.
  Access is governed by JCR permissions on `/conf/groovyconsole/reports`.
- **Migration extension** (`aem-groovy-console-migration-all`) — run-once deployment migrations replacing the
  deprecated AEM Easy Content Upgrade (AECU) project. Groovy scripts deployed below
  `/conf/groovyconsole/scripts/migration` execute with checksum-based run-once semantics in deterministic path
  order with fail-fast behavior (failed/skipped scripts stay pending and are retried on the next trigger).
  Supports `.always.groovy` re-run scripts and `author`/`publish` run-mode file name tokens. Triggered via
  `POST /bin/groovyconsole/migration` (sync, `async=true` with `runId` polling, or `dryRun=true`), a JMX MBean
  (`be.orbinson.aem.groovyconsole:type=Migration`, mirroring `AecuServiceMBean`), an opt-in debounced resource
  listener on script deployments, and a history UI at `/apps/groovyconsole/migrations.html`
  (also linked from the AEM Tools console). A run can be scoped to a single script or folder via `path=...`
  (instead of the configured scripts base path), and `data=...` (JSON or plain string, mirroring
  `AecuService.execute(path, data)`) is made available to every script in the run as the `data` binding
  variable. Run history and the per-script registry are persisted below `/var/groovyconsole/migration`.
  Installed as its own content package on top of the console; see `extensions/migration/README.md`.
  `MigrationIT` covers the API on Sling.
- **Script unit-testing support** — a new `aem-groovy-console-test-support` module: a JUnit 5 / AEM Mocks harness to
  unit-test Groovy Console scripts in a few lines (add a context plugin, run a script, assert on the result). A
  JCR-backed mock is only needed for scripts that actually touch the repository.

### Changed

- **BREAKING:** Upgraded Groovy from 4.0.31 to 5.0.6.
- **BREAKING:** Dropped Java 8 support — the minimum is now Java 11 (required by Groovy 5.x).
- Bumped exported API package versions to 20.0.0.
- The classic UI now also works on plain Sling — its Open/Save dialogs no longer depend on AEM-only client libraries.

### Security

- Hardened access control across the audit, script save/download, scheduled-jobs and services endpoints: the console
  permission is now enforced consistently, and users can only read or delete their own audit records. Previously an
  authenticated user could access another user's records.

### Fixed

- `ScheduledJobsServlet` threw a `NullPointerException` when a scheduled job had no next execution date.

## [19.1.0] - 2026-05-04

### Changed

- Upgrade Groovy from 4.0.22 to 4.0.31

### Added

- Workaround for [SLING-13123](https://issues.apache.org/jira/browse/SLING-13123): Groovy bundles became fragments in 4.0.23+, but the Sling installer's `RestartActiveBundlesTask` would still try to restart them and fail. New `GroovyBundleToFragmentFixer` removes the fragments from that task. No-op when the Sling installer is not present (e.g. AEMaaCS) or when installer-core ≥ 3.14.6 is detected.
- IT coverage for fragment-contributed extension methods (`Date.format`, `groovy.json.*`, `groovy.xml.*`)

### Fixed

- `groovy-osgi` activator now correctly registers extension modules contributed by fragment bundles (e.g. `groovy-dateutil`, `groovy-json`, `groovy-xml`). Without this, `Date.format(String, TimeZone)` and similar fragment-contributed extension methods threw `MissingMethodException` at runtime. The activator walks to the host bundle's classloader for fragments and refreshes any modules pre-registered by Groovy's own scanner with a stale classloader.
- Move `maven-gpg-plugin` to the `release` profile so local builds don't require a GPG key
- Bump `aemanalyser-maven-plugin` from 1.5.8 to 1.6.18 (was emitting an outdated-version warning)

## [19.0.9] - 2026-05-03

### Added

- Integration tests using the Sling feature model launcher [#67](https://github.com/orbinson/aem-groovy-console/pull/67)
- Documentation for AEMaaCS Publish/Preview write operations [#72](https://github.com/orbinson/aem-groovy-console/issues/72)
- Documentation of binding variables and methods [#46](https://github.com/orbinson/aem-groovy-console/issues/46)

### Changed

- Upgrade integration test instance to Sling Starter 14 and use the all content package instead of a custom feature model

### Fixed

- Installation failure on AEMaaCS due to missing `/conf/groovyconsole` parent node [#74](https://github.com/orbinson/aem-groovy-console/issues/74)

## [19.0.8] - 2024-09-16

### Changed

- Update to version of ASM that supports JDK21 [#65](https://github.com/orbinson/aem-groovy-console/issues/65)

## [19.0.7] - 2024-08-07

### Changed

- Updated to latest groovy version [#53](https://github.com/orbinson/aem-groovy-console/issues/53)

## [19.0.6] - 2024-08-07

### Added

- Add Groovy console to the Tools menu [#56](https://github.com/orbinson/aem-groovy-console/issues/56)

### Changed

- Remove all dependencies on Guava [#62](https://github.com/orbinson/aem-groovy-console/issues/62)
- Fix Cloud pipeline package Overlap Issue [#52](https://github.com/orbinson/aem-groovy-console/issues/52)

## [19.0.5] - 2024-02-10

### Changed

- Create /var/groovyconsole/audit as sling:Folder to make type conflicts: [#50](https://github.com/orbinson/aem-groovy-console/issues/50)

## [19.0.4] - 2023-08-14

### Added

- Add distribute method using Sling Content Distribution: [#39](https://github.com/orbinson/aem-groovy-console/issues/39)

### Fixed

- Only insert service when searching after pressing <kbd>enter</kbd> when using arrow keys: [#43](https://github.com/orbinson/aem-groovy-console/issues/43)

## [19.0.3] - 2023-02-21

### Fixed

- Table component did not work anymore as expected  [#41](https://github.com/orbinson/aem-groovy-console/issues/41)

## [19.0.2] - 2023-02-21

### Fixed

- Table component did not work anymore as expected  [#41](https://github.com/orbinson/aem-groovy-console/issues/41)

## [19.0.1] - 2023-02-17

### Changed

- Compile with JDK 8 as to support AEM 6.5 customers who haven't updated yet

## [19.0.0] - 2023-02-15

### Changed

- Add invalidate method, replace current invalidate with name delete: [#37](https://github.com/orbinson/aem-groovy-console/pull/37)

## [18.0.3] - 2023-01-05

### Changed

- Only internal changes to automate releases

## [18.0.2] - 2023-01-05

### Changed

- Split the bundle into an API and Core bundle and add package versions: [#31](https://github.com/orbinson/aem-groovy-console/issues/31)
- Use the bnd-baseline-maven-plugin to verify when to upgrade the package versions: [#31](https://github.com/orbinson/aem-groovy-console/issues/31)

## [18.0.1] - 2023-01-04

### Changed

- Allow to select scripts in any directory: [#29](https://github.com/orbinson/aem-groovy-console/issues/29)

### Fixed

- Publish java docs to github pages: [#19](https://github.com/orbinson/aem-groovy-console/issues/19)
- Add MetaClassExtensions extensions: [#32](https://github.com/orbinson/aem-groovy-console/issues/32)

## [18.0.0] - 2022-01-30

### Added

- Merged [aem-groovy-extension](https://github.com/icfnext/aem-groovy-extension) into this project: [#10](https://github.com/orbinson/aem-groovy-console/pull/10)
- Execute scripts on all publish instances from author environment: [#1](https://github.com/orbinson/aem-groovy-console/pull/1)
- Add xpathQuery helper method: [#1](https://github.com/orbinson/aem-groovy-console/pull/1)
- Add sql2Query helper method: [#24](https://github.com/orbinson/aem-groovy-console/pull/24)

### Changed

- Enhance project structure for cloud readiness: [#1](https://github.com/orbinson/aem-groovy-console/pull/1)
- Minimum supported version AEM 6.5.10: [#11](https://github.com/orbinson/aem-groovy-console/pull/11)
- Update documentation links: [#18](https://github.com/orbinson/aem-groovy-console/pull/18)

### Fixed

- Fix datatables error dialog: [#9](https://github.com/orbinson/aem-groovy-console/pull/9)
- Service user mapping is created automatically for bundle: [#1](https://github.com/orbinson/aem-groovy-console/pull/1)

## [17.0.0] - 2021-01-12

- Last version released by [CID15](https://github.com/CID15/aem-groovy-console)

[unreleased]: https://github.com/orbinson/aem-groovy-console/compare/19.1.0...HEAD
[19.1.0]: https://github.com/orbinson/aem-groovy-console/compare/19.0.9...19.1.0
[19.0.9]: https://github.com/orbinson/aem-groovy-console/compare/19.0.8...19.0.9
[19.0.8]: https://github.com/orbinson/aem-groovy-console/compare/19.0.7...19.0.8
[19.0.7]: https://github.com/orbinson/aem-groovy-console/compare/19.0.6...19.0.7
[19.0.6]: https://github.com/orbinson/aem-groovy-console/compare/19.0.5...19.0.6
[19.0.5]: https://github.com/orbinson/aem-groovy-console/compare/19.0.4...19.0.5
[19.0.4]: https://github.com/orbinson/aem-groovy-console/compare/19.0.3...19.0.4
[19.0.3]: https://github.com/orbinson/aem-groovy-console/compare/19.0.2...19.0.3
[19.0.2]: https://github.com/orbinson/aem-groovy-console/compare/19.0.1...19.0.2
[19.0.1]: https://github.com/orbinson/aem-groovy-console/compare/19.0.0...19.0.1
[19.0.0]: https://github.com/orbinson/aem-groovy-console/compare/18.0.3...19.0.0
[18.0.3]: https://github.com/orbinson/aem-groovy-console/compare/18.0.2...18.0.3
[18.0.2]: https://github.com/orbinson/aem-groovy-console/compare/18.0.1...18.0.2
[18.0.1]: https://github.com/orbinson/aem-groovy-console/compare/18.0.0...18.0.1
[18.0.0]: https://github.com/orbinson/aem-groovy-console/compare/17.0.0...18.0.0
[17.0.0]: https://github.com/orbinson/aem-groovy-console/releases/tag/17.0.0
