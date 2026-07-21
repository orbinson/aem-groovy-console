import type * as Monaco from 'monaco-editor';
import { compileScript } from '../api/assist-api';

const DEBOUNCE_MS = 500;
const MARKER_OWNER = 'groovy-compile';

/**
 * Server-side compile diagnostics: on (debounced) content change the script is compiled —
 * never run — with the same shell configuration as execution, and errors surface as markers.
 *
 * @return a disposer that cancels the pending debounce and detaches the change listener.
 */
export function attachGroovyDiagnostics(
  monaco: typeof Monaco,
  editor: Monaco.editor.IStandaloneCodeEditor,
): () => void {
  let timer: ReturnType<typeof setTimeout> | undefined;
  let generation = 0;

  const validate = async (): Promise<void> => {
    const model = editor.getModel();

    if (!model) {
      return;
    }

    const script = model.getValue();
    const current = ++generation;

    if (!script.trim()) {
      monaco.editor.setModelMarkers(model, MARKER_OWNER, []);
      return;
    }

    try {
      const response = await compileScript(script);

      // ignore stale responses from superseded requests
      if (current !== generation || editor.getModel() !== model) {
        return;
      }

      monaco.editor.setModelMarkers(
        model,
        MARKER_OWNER,
        response.markers.map((marker) => ({
          severity:
            marker.severity === 'warning'
              ? monaco.MarkerSeverity.Warning
              : monaco.MarkerSeverity.Error,
          message: marker.message,
          startLineNumber: marker.startLineNumber,
          startColumn: marker.startColumn,
          endLineNumber: marker.endLineNumber,
          endColumn: marker.endColumn,
        })),
      );
    } catch {
      // diagnostics are best-effort; never block the editor on errors
    }
  };

  const changeSubscription = editor.onDidChangeModelContent(() => {
    clearTimeout(timer);
    timer = setTimeout(() => void validate(), DEBOUNCE_MS);
  });

  // validate any restored content on load
  void validate();

  return () => {
    clearTimeout(timer);
    // supersede any in-flight validate() so its response is dropped
    generation++;
    changeSubscription.dispose();
  };
}
