import { html, LitElement } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import type * as Monaco from 'monaco-editor';
import { attachGroovyDiagnostics } from '@console/editor/groovy-diagnostics';
import { GROOVY_LANGUAGE_ID } from '@console/editor/groovy-language';
import { EDITOR_FONT_FAMILY, monaco, setupMonaco, syncMonacoTheme } from '@console/editor/monaco-setup';
import { persistence } from '@console/state/local-storage';

/**
 * Standalone Monaco Groovy editor for the reports editor view.  Unlike gc-script-editor it has no
 * coupling to the console's persistence/store; value flows in via the property and out via the
 * gcr-code-changed event.
 */
@customElement('gcr-code-editor')
export class GcrCodeEditor extends LitElement {
  private editor?: Monaco.editor.IStandaloneCodeEditor;
  private disposeDiagnostics?: () => void;

  @property() initialValue = '';
  @property({ type: Boolean }) readOnly = false;

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
    syncMonacoTheme(persistence.getColorScheme());

    const container = this.querySelector<HTMLDivElement>('.gcr-editor-container')!;

    this.editor = monaco.editor.create(container, {
      value: this.initialValue,
      language: GROOVY_LANGUAGE_ID,
      automaticLayout: true,
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      fontSize: 13,
      fontFamily: EDITOR_FONT_FAMILY,
      fixedOverflowWidgets: true,
      tabSize: 4,
      readOnly: this.readOnly,
      quickSuggestions: { other: true, comments: false, strings: true },
    });

    this.editor.onDidChangeModelContent(() => {
      this.dispatchEvent(new CustomEvent('gcr-code-changed', { bubbles: true, composed: true }));
    });

    this.disposeDiagnostics = attachGroovyDiagnostics(monaco, this.editor);
  }

  protected updated(changed: Map<string, unknown>): void {
    if (changed.has('readOnly')) {
      this.editor?.updateOptions({ readOnly: this.readOnly });
    }
  }

  disconnectedCallback(): void {
    this.disposeDiagnostics?.();
    this.disposeDiagnostics = undefined;
    this.editor?.dispose();
    this.editor = undefined;
    super.disconnectedCallback();
  }

  protected render() {
    return html`<div class="gcr-editor-container" aria-label="Report script editor"></div>`;
  }
}
