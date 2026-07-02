// Console UI extension module for the migration extension.
//
// This module is NOT statically imported by the console: the migration bundle announces it via a
// be.orbinson.aem.groovy.console.api.ConsoleUiExtensionProvider OSGi service, and the console SPA
// dynamically imports it and lets it register a panel through the window.GroovyConsole global
// (see the console ui.frontend's src/extensions/registry.ts for the contract).  Without the migration
// bundle installed, this module is never loaded and the console stays untouched.

// The panel uses sp-action-button, sp-badge, sp-button and sp-progress-circle but does NOT import
// them: this module is loaded into the running console page, which already registers those custom
// elements.  Re-importing them from this separate bundle would call customElements.define() a second
// time and throw.

// type-only: brings in the window.GroovyConsole global declaration without bundling the registry
import type {} from '@console/extensions/registry';

import './extensions/migration/gc-migration';

const ICON = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 18 18" width="18" height="18" fill="currentColor" aria-hidden="true">
  <path d="M9 1.5 4.5 6h3v5h3V6h3L9 1.5z"/>
  <rect x="3" y="12.5" width="12" height="1.8" rx="0.5"/>
  <rect x="3" y="15.2" width="12" height="1.8" rx="0.5"/>
</svg>`;

window.GroovyConsole?.registerPanel({
  id: 'migrations',
  title: 'Migrations',
  element: 'gc-migration',
  iconHtml: ICON,
});
