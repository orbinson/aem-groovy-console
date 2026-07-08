// Minimal, self-contained replacement for the shared modern-UI frontend infrastructure
// (`ui.frontend/src/state/local-storage.ts`) that this extension's standalone page used to import
// via the `@console` alias. Only the color-scheme preference is used by the migration UI, so that's
// all this shim carries. Uses the same localStorage key as the (not shipped on this line) modern UI
// for continuity if a project later moves to a release that does ship it.

const THEME = 'groovyconsole.theme';

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
  getColorScheme: (): 'light' | 'dark' => (get(`${THEME}.modern`) === 'dark' ? 'dark' : 'light'),
  saveColorScheme: (value: 'light' | 'dark'): void => set(`${THEME}.modern`, value),
};
