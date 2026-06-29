import { html, LitElement } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import type * as Monaco from 'monaco-editor';
import { attachGroovyDiagnostics } from '../../editor/groovy-diagnostics';
import { GROOVY_LANGUAGE_ID } from '../../editor/groovy-language';
import { monaco, setupMonaco } from '../../editor/monaco-setup';

/**
 * Standalone Monaco Groovy editor for the reports editor view.  Unlike gc-script-editor it has no
 * coupling to the console's persistence/store; value flows in via the property and out via the
 * gcr-code-changed event.
 */
@customElement('gcr-code-editor')
export class GcrCodeEditor extends LitElement {
  private editor?: Monaco.editor.IStandaloneCodeEditor;

  @property() initialValue = '';

  // Render in light DOM so Monaco's document-level styles apply.
  createRenderRoot(): this {
    return this;
  }

  get value(): string {
    return this.editor?.getValue() ?? this.initialValue;
  }

  set value(value: string) {
    this.editor?.setValue(value);
  }

  protected firstUpdated(): void {
    setupMonaco();

    const container = this.querySelector<HTMLDivElement>('.gcr-editor-container')!;

    this.editor = monaco.editor.create(container, {
      value: this.initialValue,
      language: GROOVY_LANGUAGE_ID,
      automaticLayout: true,
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      fontSize: 13,
      fixedOverflowWidgets: true,
      tabSize: 4,
      quickSuggestions: { other: true, comments: false, strings: true },
    });

    this.editor.onDidChangeModelContent(() => {
      this.dispatchEvent(new CustomEvent('gcr-code-changed', { bubbles: true, composed: true }));
    });

    attachGroovyDiagnostics(monaco, this.editor);
  }

  disconnectedCallback(): void {
    this.editor?.dispose();
    super.disconnectedCallback();
  }

  protected render() {
    return html`<div class="gcr-editor-container" aria-label="Report script editor"></div>`;
  }
}
