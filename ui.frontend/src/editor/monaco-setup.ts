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

let initialized = false;

export function setupMonaco(): typeof monaco {
  if (!initialized) {
    initialized = true;
    registerGroovyLanguage(monaco);
    registerGroovyCompletionProvider(monaco);
    registerGroovyHoverProvider(monaco);
  }
  return monaco;
}

export function syncMonacoTheme(_colorScheme: 'light' | 'dark'): void {
  // The editors keep a dark theme in both color schemes, matching the classic console's
  // default dark editor (idle_fingers) on a light page.
  monaco.editor.setTheme('vs-dark');
}

export { monaco };
