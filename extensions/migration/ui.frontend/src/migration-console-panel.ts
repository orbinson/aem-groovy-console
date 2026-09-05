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

// A launching rocket (nose cone, body, swept fins, exhaust flame) with an upward arrow cut out of the body,
// evoking a deployment upgrade/migration. Flat single-colour glyph in the Spectrum workflow-icon style.
const ICON = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 18 18" width="18" height="18" fill="currentColor" aria-hidden="true">
  <path fill-rule="evenodd" d="M9 1.3 11.3 5.2V11.5H6.7V5.2L9 1.3ZM9 5.8 7.7 7.6H8.4V10.3H9.6V7.6H10.3L9 5.8Z"/>
  <path d="M6.7 9.1 4.4 12.4 6.7 11.6V9.1Z"/>
  <path d="M11.3 9.1V11.6L13.6 12.4 11.3 9.1Z"/>
  <path d="M7.4 11.7 9 15.8 10.6 11.7A5.4 5.4 0 0 1 7.4 11.7Z"/>
</svg>`;

window.GroovyConsole?.registerPanel({
  id: 'migrations',
  title: 'Migrations',
  element: 'gc-migration',
  iconHtml: ICON,
});
