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
  debounced resource listener on script deployments, a history UI at `/apps/groovyconsole/migrations.html`
  and a Migrations drawer in the modern console. Run history and the per-script registry are persisted below
  `/var/groovyconsole/migration`.
- **Modern UI** — an IDE-style console built with [Spectrum Web Components](https://opensource.adobe.com/spectrum-web-components/)
  and the [Monaco Editor](https://microsoft.github.io/monaco-editor/), in a new `ui.frontend` module (Lit + TypeScript +
  Vite). It has no AEM Granite/Coral dependency and runs on AEM and plain Sling. Features a resizable editor/output split,
  a tabbed output dock (Log/Result/Table/Trace), slide-out drawers for History, Scheduled Jobs and Help, and a status bar.
- **Code assistance** in the modern editor, backed by new `/bin/groovyconsole/assist/*` endpoints: class completion over
  the OSGi class space (with auto-import), member/method completion including Groovy extension (GDK) methods, binding and
  star-import awareness, hover documentation, OSGi service-name completion inside `getService("...")`, and live
  compile-error diagnostics (parse-only, sharing the script execution's compiler configuration).
- **Streaming script output** — execute asynchronously with `POST /bin/groovyconsole/post.json?async=true` (returns an
  `executionId`) and poll `GET /bin/groovyconsole/stream.json`; output is delivered while the script runs. Both UIs use it,
  and the classic UI streams into its output panel. Backwards compatible: without the `async` parameter the endpoint
  behaves exactly as before, so existing clients (e.g. the IntelliJ plugin) are unaffected.
- Clickable stack-trace frames in the modern UI that jump to the offending line in the editor.
- **Default UI** OSGi property (`be.orbinson.aem.groovy.console.configuration.impl.DefaultConfigurationService`) selecting
  which UI `/groovyconsole` serves; both UIs are always reachable via the `.modern.html` / `.classic.html` selectors.
- New `ui.tests` module with Playwright end-to-end tests, and a `run` Maven profile (`mvn verify -Prun -pl it.tests`) that
  launches the aggregated Sling feature model on a fixed port for manual testing.
- `AssistIT` and `StreamingIT` integration tests.
- **Extension packages** — an `extensions/` area for opt-in features that ship as their own content packages, kept out
  of the console's `all` package so the core install stays lean. The console has no dependency on any extension and is
  fully functional without them; extensions integrate only through public SPIs, including the new
  `be.orbinson.aem.groovy.console.api.ConsoleUiExtensionProvider`, which lets a bundle contribute panels to the modern
  UI's activity rail (loaded dynamically via `window.GroovyConsole.registerPanel(...)`).
- **Reports extension** (`aem-groovy-console-reports-all`) — business-facing reports backed by Groovy scripts: named
  report definitions with an inline Groovy script and typed parameters under `/conf/groovyconsole/reports`,
  asynchronous run-once executions (the request returns immediately and clients poll) with persisted, paginated
  tabular results, API-driven CSV/XLSX export (XLSX via AEM's `com.adobe.granite.poi` or the ServiceMix POI bundles on
  Sling), scheduled execution purging, a business UI at `/apps/groovyconsole/reports.html`, and a Reports drawer in the
  modern console. Access control is governed entirely by **JCR permissions** on `/conf/groovyconsole/reports` (read =
  view/run, write = create/edit/delete) — there are no application-level access groups. See
  `extensions/reports/README.md`. `GroovyConsoleReportsIT` covers the API and export wiring on Sling.

### Changed

- **BREAKING:** Upgrade Groovy from 4.0.31 to 5.0.6
- **BREAKING:** Drop Java 8 support — minimum is now Java 11 (Groovy 5.x requires JDK 11+)
- Bump exported API package versions to 20.0.0
- The **modern UI is now the default** at `/groovyconsole`; the classic UI remains available
  at `/apps/groovyconsole.classic.html`.
- Classic UI: replaced the AEM-only ExtJS Open/Save dialogs with Bootstrap modals that also work on plain Sling, and
  dropped the `cq.wcm.edit` and `cq.shared` clientlib dependencies (the CSRF token is now provided via
  `granite.csrf.standalone`).

### Fixed

- `ScheduledJobsServlet` threw a `NullPointerException` on plain Sling when a scheduled job had no next execution date.
- The OSGi services listing servlet (`/bin/groovyconsole/services`) now enforces the console permission check.
- CSRF token handling for the modern UI's POST/DELETE requests on AEM author instances.

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
