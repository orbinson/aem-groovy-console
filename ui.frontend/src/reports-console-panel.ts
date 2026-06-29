// Console UI extension module for the reports extension.
//
// This module is NOT statically imported by the console: the reports bundle announces it via a
// be.orbinson.aem.groovy.console.api.ConsoleUiExtensionProvider OSGi service, and the console SPA
// dynamically imports it and lets it register a panel through the window.GroovyConsole global
// (see src/extensions/registry.ts for the contract).  Without the reports bundle installed, this
// module is never loaded and the console stays untouched.

// Spectrum Web Components used by the panel (deduplicated with the console bundle at build time)
import '@spectrum-web-components/action-button/sp-action-button.js';
import '@spectrum-web-components/badge/sp-badge.js';
import '@spectrum-web-components/button/sp-button.js';
import '@spectrum-web-components/progress-circle/sp-progress-circle.js';
import '@spectrum-web-components/search/sp-search.js';

import './extensions/reports/gc-reports';

const ICON = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 18 18" width="18" height="18" fill="currentColor" aria-hidden="true">
  <rect x="2" y="9" width="3" height="7" rx="0.5"/>
  <rect x="7.5" y="5" width="3" height="11" rx="0.5"/>
  <rect x="13" y="2" width="3" height="14" rx="0.5"/>
</svg>`;

window.GroovyConsole?.registerPanel({
  id: 'reports',
  title: 'Reports',
  element: 'gc-reports',
  iconHtml: ICON,
});
