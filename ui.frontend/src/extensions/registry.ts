import { config } from '../config';
import type { RunScriptResponse } from '../api/types';

/**
 * Console UI extension mechanism.
 *
 * Backend bundles announce ES modules by registering a
 * `be.orbinson.aem.groovy.console.api.ConsoleUiExtensionProvider` OSGi service; the URLs arrive in the
 * page config (`uiExtensions`) and are dynamically imported here.  Each module registers panels, run
 * actions, and result tabs via the `window.GroovyConsole` global.
 *
 * Extension modules must be self-contained (define their own custom elements and styles) and interact
 * with the console exclusively through:
 *
 * - `window.GroovyConsole.registerPanel({ id, title, element, iconHtml })` — activity-rail drawer panel
 * - `window.GroovyConsole.registerRunAction({ id, label, run })` — entry in the Run button's options menu
 * - `window.GroovyConsole.registerRunResultTab({ id, label, element, isRelevant })` — tab in the output dock
 * - `window.GroovyConsole.getScript()` — the script currently in the editor (empty string if none)
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

/** A run result as stored by the shell: a RunScriptResponse plus any extra fields a run action attached. */
export type ExtendedRunResult = RunScriptResponse & Record<string, unknown>;

export interface ConsoleRunActionExtension {
  /** Unique action id. */
  id: string;
  /** Menu entry label, e.g. "Run with query audit". */
  label: string;
  /**
   * Execute the current script. The returned object is stored as the run result and rendered by the
   * output dock; extra fields beyond RunScriptResponse are kept and can drive a registered result tab.
   */
  run(context: { script: string; data: string }): Promise<ExtendedRunResult>;
}

export interface ConsoleRunResultTabExtension {
  /** Unique tab id. */
  id: string;
  /** Tab label in the output dock. */
  label: string;
  /** Custom element tag rendered as the tab body; the dock assigns the run result to its `result` property. */
  element: string;
  /** Whether this tab applies to the given run result (e.g. the result carries the extension's extra field). */
  isRelevant(result: ExtendedRunResult): boolean;
}

export interface GroovyConsoleGlobal {
  registerPanel(panel: ConsolePanelExtension): void;
  registerRunAction(action: ConsoleRunActionExtension): void;
  registerRunResultTab(tab: ConsoleRunResultTabExtension): void;
  /** The script currently in the editor, or an empty string. */
  getScript(): string;
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
const runActions: ConsoleRunActionExtension[] = [];
const runResultTabs: ConsoleRunResultTabExtension[] = [];
const listeners = new Set<() => void>();

/** Set by the console shell so extensions can read the editor's current script via `window.GroovyConsole.getScript()`. */
let activeScriptProvider: (() => string) | null = null;

export function setActiveScriptProvider(provider: () => string): void {
  activeScriptProvider = provider;
}

export function getPanels(): readonly ConsolePanelExtension[] {
  return panels;
}

export function getRunActions(): readonly ConsoleRunActionExtension[] {
  return runActions;
}

export function getRunResultTabs(): readonly ConsoleRunResultTabExtension[] {
  return runResultTabs;
}

/** Notifies on any extension registration (panels, run actions, result tabs). */
export function onExtensionsChanged(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function notify(): void {
  listeners.forEach((listener) => listener());
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
  notify();
}

function registerRunAction(action: ConsoleRunActionExtension): void {
  if (!action?.id || !action.label || typeof action.run !== 'function'
      || runActions.some((existing) => existing.id === action.id)) {
    return;
  }
  runActions.push(action);
  notify();
}

function registerRunResultTab(tab: ConsoleRunResultTabExtension): void {
  if (!tab?.id || !tab.label || typeof tab.isRelevant !== 'function'
      || runResultTabs.some((existing) => existing.id === tab.id)) {
    return;
  }
  if (!CUSTOM_ELEMENT_NAME.test(tab.element)) {
    console.error(`[groovyconsole] ignoring result tab "${tab.id}": invalid element name "${tab.element}"`);
    return;
  }
  runResultTabs.push(tab);
  notify();
}

let initialized = false;

/** Expose the registration global and load the announced extension modules. */
export function initConsoleExtensions(): void {
  if (initialized) {
    return;
  }
  initialized = true;

  window.GroovyConsole = {
    registerPanel,
    registerRunAction,
    registerRunResultTab,
    getScript: () => (activeScriptProvider ? activeScriptProvider() : ''),
  };

  for (const url of config.uiExtensions) {
    import(/* @vite-ignore */ `${config.contextPath}${url}`).catch((error: unknown) => {
      console.error(`[groovyconsole] failed to load UI extension module ${url}`, error);
    });
  }
}
