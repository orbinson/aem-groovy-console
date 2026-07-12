import * as monaco from 'monaco-editor';
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker';
import { registerGroovyCompletionProvider } from './groovy-completion';
import { registerGroovyHoverProvider } from './groovy-hover';
import { registerGroovyLanguage } from './groovy-language';

// Bundle the workers with Vite instead of loading them from a CDN; they are emitted
// under the configured base path so they stay same-origin with the JCR-served page.
self.MonacoEnvironment = {
  getWorker(_workerId: string, label: string): Worker {
    if (label === 'json') {
      return new jsonWorker();
    }
    return new editorWorker();
  },
};

/** Same code font stack as --spectrum-code-font-family-stack (tokens.css); Monaco needs it explicitly. */
export const EDITOR_FONT_FAMILY = "'Source Code Pro', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace";

let initialized = false;

export function setupMonaco(): typeof monaco {
  if (!initialized) {
    initialized = true;

    // Editor themes that sit on the Spectrum surface scale, so the editor reads as a pane
    // of the app instead of a foreign dark/light rectangle (gray-50 light, gray-100 dark).
    monaco.editor.defineTheme('gc-light', {
      base: 'vs',
      inherit: true,
      rules: [],
      colors: {
        'editor.background': '#ffffff',
        'editor.lineHighlightBackground': '#f5f5f5',
        'editorLineNumber.foreground': '#b1b1b1',
        'editorLineNumber.activeForeground': '#505050',
        'editorGutter.background': '#ffffff',
      },
    });
    monaco.editor.defineTheme('gc-dark', {
      base: 'vs-dark',
      inherit: true,
      rules: [],
      colors: {
        'editor.background': '#1d1d1d',
        'editor.lineHighlightBackground': '#252525',
        'editorLineNumber.foreground': '#6e6e6e',
        'editorLineNumber.activeForeground': '#b1b1b1',
        'editorGutter.background': '#1d1d1d',
      },
    });

    registerGroovyLanguage(monaco);
    registerGroovyCompletionProvider(monaco);
    registerGroovyHoverProvider(monaco);

    // Hosts that lazy-load Monaco (reports editor) announce scheme changes via this window
    // event instead of importing this module eagerly, which would defeat the code split.
    window.addEventListener('gc-color-scheme-changed', ((event: CustomEvent<{ colorScheme: 'light' | 'dark' }>) => {
      syncMonacoTheme(event.detail.colorScheme);
    }) as EventListener);

    // Monaco measures glyph widths at editor creation; if the bundled webfont arrives later,
    // remeasure so the cursor and selection don't drift from the rendered text.
    document.fonts?.ready.then(() => monaco.editor.remeasureFonts());
  }
  return monaco;
}

export function syncMonacoTheme(colorScheme: 'light' | 'dark'): void {
  setupMonaco();
  monaco.editor.setTheme(colorScheme === 'dark' ? 'gc-dark' : 'gc-light');
}

export { monaco };
