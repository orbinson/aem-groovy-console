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

// Rocket (nose up) with an upward arrow cut out of the body, evoking a deployment upgrade/migration.
const ICON = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 18 18" width="18" height="18" fill="currentColor" aria-hidden="true">
  <path fill-rule="evenodd" d="M9 1c2.4 2.4 3.6 5.4 3.6 8.6V12H5.4V9.6C5.4 6.4 6.6 3.4 9 1Zm0 3.1L7 6.8h1.1v3h1.8v-3H11L9 4.1Z"/>
  <path d="M5.4 12L3 15.2l2.9-1.1V12H5.4Z"/>
  <path d="M12.6 12v2.1l2.9 1.1L12.6 12H12.6Z"/>
  <path d="M7.7 13.5L9 16.8l1.3-3.3a4 4 0 0 1-2.6 0Z"/>
</svg>`;

window.GroovyConsole?.registerPanel({
  id: 'migrations',
  title: 'Migrations',
  element: 'gc-migration',
  iconHtml: ICON,
});
