// Same localStorage keys as the classic UI (clientlibs/js/local-storage.js) so
// editor content, theme, and script name survive switching between the two UIs.

const EDITOR_HEIGHT = 'groovyconsole.editor.height';
const EDITOR_CONTENT = 'groovyconsole.editor.content';
const THEME = 'groovyconsole.theme';
const SCRIPT_NAME = 'groovyconsole.script.name';

function get(name: string, defaultValue = ''): string {
  try {
    return window.localStorage.getItem(name) ?? defaultValue;
  } catch {
    return defaultValue;
  }
}

function set(name: string, value: string): void {
  try {
    window.localStorage.setItem(name, value);
  } catch {
    // localStorage unavailable (private mode etc.) — non-fatal
  }
}

export const persistence = {
  getScriptEditorContent: (): string => get(`${EDITOR_CONTENT}.script`),
  saveScriptEditorContent: (value: string): void => set(`${EDITOR_CONTENT}.script`, value),

  getDataEditorContent: (): string => get(`${EDITOR_CONTENT}.data`),
  saveDataEditorContent: (value: string): void => set(`${EDITOR_CONTENT}.data`, value),

  getScriptEditorHeight: (defaultValue: string): string => get(`${EDITOR_HEIGHT}.script`, defaultValue),
  saveScriptEditorHeight: (value: string): void => set(`${EDITOR_HEIGHT}.script`, value),

  getDataEditorHeight: (defaultValue: string): string => get(`${EDITOR_HEIGHT}.data`, defaultValue),
  saveDataEditorHeight: (value: string): void => set(`${EDITOR_HEIGHT}.data`, value),

  // The classic UI stores Ace theme ids ("ace/theme/..."); the modern UI stores
  // "light"/"dark" under its own key to avoid clobbering the classic preference.
  getColorScheme: (): 'light' | 'dark' => (get(`${THEME}.modern`) === 'dark' ? 'dark' : 'light'),
  saveColorScheme: (value: 'light' | 'dark'): void => set(`${THEME}.modern`, value),

  getSplitterPosition: (): number | null => {
    const value = get('groovyconsole.modern.splitter');
    return value ? Number(value) : null;
  },
  saveSplitterPosition: (value: number): void => set('groovyconsole.modern.splitter', String(value)),

  getScriptName: (): string => get(SCRIPT_NAME),
  saveScriptName: (scriptPath: string): void => {
    let scriptName = scriptPath;
    if (scriptPath.indexOf('.groovy') > 0) {
      scriptName = scriptPath.substring(scriptPath.lastIndexOf('/') + 1, scriptPath.indexOf('.'));
    }
    set(SCRIPT_NAME, scriptName);
  },
  clearScriptName: (): void => set(SCRIPT_NAME, ''),
};
