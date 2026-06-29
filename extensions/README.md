# Groovy Console Extensions

This directory holds **optional, opt-in features** built on top of the AEM Groovy Console. Each extension is a
set of Maven modules that ships as its **own content package** — it is *not* embedded in the console's `all`
package. The console has no compile-time or runtime dependency on any extension and is fully functional with
none installed; installing an extension's package adds its capability, removing it takes the capability away
cleanly.

See the [root README](../README.md#extension-packages) for the philosophy. This document is the practical guide
for **adding a new extension**.

The [`reports/`](reports) extension is the reference implementation — copy its structure.

## How an extension integrates with the console

An extension may only touch the console through its **public APIs** — never by patching console modules:

| Integration point | API | Use it to |
|---|---|---|
| Script execution | `GroovyConsoleService.runScript(ScriptContext)` | Run Groovy with all console bindings, star imports, metaclasses, timeout and audit. |
| Bindings / imports / metaclasses | `BindingExtensionProvider`, `StarImportExtensionProvider`, `CompilationCustomizerExtensionProvider`, `ScriptMetaClassExtensionProvider` | Add your own bindings/imports for scripts your extension runs (gate on your own `ScriptContext` marker type). |
| Modern UI panels | `be.orbinson.aem.groovy.console.api.ConsoleUiExtensionProvider` (OSGi service returning ES module URLs) | Contribute a panel to the console's activity rail. |
| Its own pages/endpoints | Path-bound Sling servlets under your own path | Serve your extension's UI shell and JSON API; they exist only when the extension is installed. |

On the frontend, an announced module is loaded dynamically by the console SPA and registers panels via the
`window.GroovyConsole.registerPanel({ id, title, element, iconHtml })` global. Panels are self-contained custom
elements and talk to the shell **only through DOM events** — `gc-set-script` (`{ script, message? }`) and
`gc-toast` (`{ message, variant? }`) — never via imports. The console stays fully functional with no providers.

## Module layout (mirror `reports/`)

```
extensions/
├── pom.xml                      aggregator: aem-groovy-console-extensions (pom) — lists each extension module
└── <name>/
    ├── pom.xml                  aem-groovy-console-<name> (pom) — lists the sub-modules below
    ├── api/                     exported value types + SPI interfaces (bundle)
    ├── bundle/                  services, servlets, providers (bundle; Groovy like the console)
    ├── exporter-*/ (optional)   isolate optional 3rd-party deps in their own bundle (e.g. POI for xlsx)
    ├── ui.config/               repoinit (paths, service user, ACLs), ServiceUserMapper amendment, OSGi configs
    ├── ui.content/              sample/seed content under /conf or /var
    └── all/                     content-package container embedding the bundles + ui.config + ui.content
```

Frontend code (if any) lives in the shared `ui.frontend` module as additional Vite entries — see
`ui.frontend/reports.html`, `src/reports-main.ts`, `src/components/reports/` and `src/extensions/reports/` for
the pattern. The built assets are served from `/apps/groovyconsole/spa/assets/` by a path-bound page servlet in
the extension bundle (so the page only exists when the extension is installed).

## Checklist for a new extension

1. **Scaffold** the modules under `extensions/<name>/` (copy `reports/` and rename). Keep the Java/Groovy package
   root `be.orbinson.aem.groovy.console.<name>`.
2. **Version**: inherit the reactor version (currently `20.0.0-SNAPSHOT`) via the parent — do not hardcode.
3. **Wire the build**: add `<name>` to `extensions/pom.xml` `<modules>`; the `extensions` module is already in the
   root `pom.xml`.
4. **Depend on the console** via reactor dependencies (`aem-groovy-console-api`, scope `provided`) — never bundle it.
5. **Access control**: define a service user + ACLs scoped to *only* your paths in `ui.config` repoinit, plus a
   `ServiceUserMapper` amendment. Enforce permissions in every servlet *before* any service-resolver read.
6. **Optional 3rd-party deps** (e.g. POI): isolate them in a separate bundle with wide OSGi import ranges so the
   core extension degrades gracefully when the provider is absent (see `reports/exporter-xlsx`).
7. **UI panel** (optional): register a `ConsoleUiExtensionProvider` returning your ES module URL(s); build the
   module as a Vite entry; register panels via `window.GroovyConsole.registerPanel(...)`.
8. **Package**: the `all` module embeds your bundles + content packages into a single
   `aem-groovy-console-<name>-all` zip. Do **not** add it to the console's `all` package.
9. **Tests**:
   - Unit (Groovy + JUnit 5) in `bundle`/`exporter-*` — note: keep an empty `src/test/java/.gitkeep` so the
     groovy-eclipse compiler picks up `src/test/groovy` (git does not track empty dirs).
   - Integration: add a `groovyconsole-<name>.json` Sling feature to `it.tests/src/main/features/` (it is picked
     up by the `*.json` aggregate) and a `*IT` test; do the same under `ui.tests/` for Playwright e2e.
10. **Document** the extension in the [root README](../README.md#extension-packages) "Available extensions" table.

## Deploying

Install the console first, then your extension's `aem-groovy-console-<name>-all` content package. On AEM, rely on
platform-provided libraries where possible (e.g. `com.adobe.granite.poi`); on plain Sling, document any extra
bundles the extension needs (see the `reports` README/notes for the ServiceMix POI set).
