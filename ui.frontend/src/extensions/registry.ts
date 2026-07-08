import { config } from '../config';

/**
 * Console UI extension mechanism.
 *
 * Backend bundles announce ES modules by registering a
 * `be.orbinson.aem.groovy.console.api.ConsoleUiExtensionProvider` OSGi service; the URLs arrive in the
 * page config (`uiExtensions`) and are dynamically imported here.  Each module registers panels for the
 * console's activity rail via the `window.GroovyConsole.registerPanel(...)` global.
 *
 * Extension modules must be self-contained (define their own custom elements and styles) and interact
 * with the console exclusively through:
 *
 * - `window.GroovyConsole.registerPanel({ id, title, element, iconHtml })`
 * - DOM events (bubbling, composed) handled by the console shell:
 *   - `gc-set-script`  detail: `{ script: string; message?: string }` — load a script into the editor
 *   - `gc-toast`       detail: `{ message: string; variant?: 'positive' | 'negative' }` — show a toast
 *
 * This keeps the console fully functional without any extensions installed, and lets extensions (e.g. the
 * reports extension) hook in without compile-time coupling.
 */
export interface ConsolePanelExtension {
  /** Unique panel id, also used as the drawer identifier. */
  id: string;
  /** Drawer heading and rail button tooltip. */
  title: string;
  /** Custom element tag name rendered inside the drawer; defined by the extension module. */
  element: string;
  /** Inline SVG markup for the activity rail button (18×18, currentColor). */
  iconHtml?: string;
}

export interface GroovyConsoleGlobal {
  registerPanel(panel: ConsolePanelExtension): void;
}

declare global {
  interface Window {
    GroovyConsole?: GroovyConsoleGlobal;
  }
}

/** A valid custom-element tag name: starts with a lowercase letter and contains a hyphen. Panels declare
 *  their element as a string that the shell renders as a tag, so reject anything else to keep it from being
 *  used as an HTML-injection primitive. */
const CUSTOM_ELEMENT_NAME = /^[a-z][a-z0-9]*-[a-z0-9-]*$/;

const panels: ConsolePanelExtension[] = [];
const listeners = new Set<() => void>();

export function getPanels(): readonly ConsolePanelExtension[] {
  return panels;
}

export function onPanelsChanged(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function registerPanel(panel: ConsolePanelExtension): void {
  if (!panel?.id || !panel.element || panels.some((existing) => existing.id === panel.id)) {
    return;
  }
  if (!CUSTOM_ELEMENT_NAME.test(panel.element)) {
    console.error(`[groovyconsole] ignoring panel "${panel.id}": invalid element name "${panel.element}"`);
    return;
  }
  panels.push(panel);
  listeners.forEach((listener) => listener());
}

let initialized = false;

/** Expose the registration global and load the announced extension modules. */
export function initConsoleExtensions(): void {
  if (initialized) {
    return;
  }
  initialized = true;

  window.GroovyConsole = { registerPanel };

  for (const url of config.uiExtensions) {
    import(/* @vite-ignore */ `${config.contextPath}${url}`).catch((error: unknown) => {
      console.error(`[groovyconsole] failed to load UI extension module ${url}`, error);
    });
  }
}
