// Minimal, self-contained replacement for the shared modern-UI frontend infrastructure
// (`ui.frontend/src/config.ts`) that this extension's standalone page used to import via the
// `@console` alias. The 19.x line does not ship the modern UI/its `ui.frontend` module, so the
// migration frontend carries its own tiny subset here instead of aliasing a sibling module.
//
// Only the fields the migration UI actually reads are kept: `contextPath` (used to build API URLs)
// and `aem` (gates CSRF-token fetching for mutating requests on AEM author).

export interface GcConfig {
  contextPath: string;
  aem: boolean;
}

declare global {
  interface Window {
    __GC_CONFIG__?: GcConfig;
  }
}

// Dev fallback: MigrationPageServlet injects window.__GC_CONFIG__ in production; the Vite dev
// server has no server-side model, so default to a permissive Sling-flavoured config.
const devDefaults: GcConfig = {
  contextPath: '',
  aem: new URLSearchParams(window.location.search).get('aem') === 'true',
};

export const config: GcConfig = { ...devDefaults, ...(window.__GC_CONFIG__ ?? {}) };
