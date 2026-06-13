import { html, LitElement } from 'lit';
import { customElement } from 'lit/decorators.js';
import type * as Monaco from 'monaco-editor';
import { attachGroovyDiagnostics } from '../editor/groovy-diagnostics';
import { GROOVY_LANGUAGE_ID } from '../editor/groovy-language';
import { monaco, setupMonaco } from '../editor/monaco-setup';
import { persistence } from '../state/local-storage';
import { store } from '../state/store';

@customElement('gc-script-editor')
export class GcScriptEditor extends LitElement {
  private editor?: Monaco.editor.IStandaloneCodeEditor;

  // Render in light DOM so Monaco's document-level styles apply.
  createRenderRoot(): this {
    return this;
  }

  get value(): string {
    return this.editor?.getValue() ?? '';
  }

  set value(value: string) {
    this.editor?.setValue(value);
  }

  get monacoEditor(): Monaco.editor.IStandaloneCodeEditor | undefined {
    return this.editor;
  }

  setReadOnly(readOnly: boolean): void {
    this.editor?.updateOptions({ readOnly });
  }

  protected firstUpdated(): void {
    setupMonaco();

    const container = this.querySelector<HTMLDivElement>('.gc-editor-container')!;

    this.editor = monaco.editor.create(container, {
      value: persistence.getScriptEditorContent(),
      language: GROOVY_LANGUAGE_ID,
      automaticLayout: true,
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      fontSize: 13,
      fixedOverflowWidgets: true,
      tabSize: 4,
      // strings: service-name completion lives inside getService("...") string literals
      quickSuggestions: { other: true, comments: false, strings: true },
    });

    this.editor.onDidChangeModelContent(() => {
      persistence.saveScriptEditorContent(this.value);
      store.setState({ dirty: true });
    });

    this.editor.onDidChangeCursorPosition((event) => {
      window.dispatchEvent(
        new CustomEvent('gc-cursor', {
          detail: { line: event.position.lineNumber, column: event.position.column },
        }),
      );
    });

    // Ctrl/Cmd+Enter runs the script (classic UI parity)
    this.editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      this.dispatchEvent(new CustomEvent('gc-run', { bubbles: true, composed: true }));
    });

    // Ctrl/Cmd+S saves the script
    this.editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
      this.dispatchEvent(new CustomEvent('gc-save', { bubbles: true, composed: true }));
    });

    // Ctrl/Cmd+K opens the command palette (F1 is awkward on macOS keyboards)
    this.editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyK, () => {
      this.editor?.trigger('gc', 'editor.action.quickCommand', {});
    });

    // Insert an OSGi service lookup and let the existing getService("...") completion take over
    this.editor.addAction({
      id: 'gc.insertService',
      label: 'Insert OSGi service…',
      contextMenuGroupId: 'gc',
      contextMenuOrder: 1,
      run: (editor) => {
        const position = editor.getPosition();
        if (!position) {
          return;
        }

        const snippet = 'def service = getService("")';
        editor.executeEdits('gc.insertService', [
          {
            range: {
              startLineNumber: position.lineNumber,
              startColumn: position.column,
              endLineNumber: position.lineNumber,
              endColumn: position.column,
            },
            text: snippet,
          },
        ]);
        // place the cursor inside the quotes and trigger the service-name completion
        editor.setPosition({ lineNumber: position.lineNumber, column: position.column + snippet.length - 2 });
        editor.trigger('gc.insertService', 'editor.action.triggerSuggest', {});
      },
    });

    attachGroovyDiagnostics(monaco, this.editor);
  }

  protected render() {
    return html`<div class="gc-editor-container" aria-label="Groovy script editor"></div>`;
  }
}
