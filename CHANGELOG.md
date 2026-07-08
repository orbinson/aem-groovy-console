# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Migration extension** (`aem-groovy-console-migration-all`) — run-once deployment migrations replacing the
  deprecated AEM Easy Content Upgrade (AECU) project. Groovy scripts deployed below
  `/conf/groovyconsole/scripts/migration` execute with checksum-based run-once semantics in deterministic path
  order with fail-fast behavior (failed/skipped scripts stay pending and are retried on the next trigger).
  Supports `.always.groovy` re-run scripts and `author`/`publish` run-mode file name tokens. Triggered via
  `POST /bin/groovyconsole/migration` (sync, `async=true` with `runId` polling, or `dryRun=true`), an opt-in
  debounced resource listener on script deployments, and a history UI at `/apps/groovyconsole/migrations.html`
  (also linked from the AEM Tools console). A run can be scoped to a single script or folder via `path=...`
  (instead of the configured scripts base path), and `data=...` (JSON or plain string, mirroring
  `AecuService.execute(path, data)`) is made available to every script in the run as the `data` binding
  variable. Run history and the per-script registry are persisted below `/var/groovyconsole/migration`.
  Installed as its own content package on top of the console; see `extensions/migration/README.md`.
  `MigrationIT` covers the API on Sling.
- **Extension packages** — an `extensions/` area for opt-in features that ship as their own content packages,
  kept out of the console's `all` package so the core install stays lean. The console has no dependency on any
  extension and is fully functional without them.

### Changed

- Upgrade Groovy from 4.0.31 to 4.0.32.
- Upgrade ASM (vendored for Groovy's runtime bytecode generation) from 9.7 to 9.10.1, for compatibility with
  newer JVMs (up to Java 25).

### Security

- Audit endpoints now enforce access control: the audit servlet (`/bin/groovyconsole/audit`) and the script
  download servlet (`/bin/groovyconsole/download`) require the console permission and only return/delete a
  record the requesting user owns (or scheduled-job records with the scheduled-job permission, or any record
  when "display all audit records" is enabled). Previously any authenticated user could read or delete another
  user's audit records by supplying their user ID.
- `ScheduledJobsServlet` now enforces the scheduled-job permission on its GET (job listing), matching its
  POST/DELETE (it previously leaked every scheduled job's script and download link).
- The script save servlet (`/bin/groovyconsole/save`) now enforces the console permission.
- Groovy compilation errors are now audited and notified, consistent with runtime errors.
- Audit date-range filtering no longer mutates the record's timestamp (time-of-day is preserved for display).
- The console permission check no longer throws a `NullPointerException` when the request principal cannot be
  resolved to a user (e.g. a deleted user with a still-valid session); it now denies access instead.

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
