import { html, LitElement } from 'lit';
import { customElement } from 'lit/decorators.js';
import type * as Monaco from 'monaco-editor';
import { EDITOR_FONT_FAMILY, monaco, setupMonaco } from '../editor/monaco-setup';
import { persistence } from '../state/local-storage';

@customElement('gc-data-editor')
export class GcDataEditor extends LitElement {
  private editor?: Monaco.editor.IStandaloneCodeEditor;

  createRenderRoot(): this {
    return this;
  }

  disconnectedCallback(): void {
    this.editor?.dispose();
    this.editor = undefined;
    super.disconnectedCallback();
  }

  get value(): string {
    return this.editor?.getValue() ?? '';
  }

  set value(value: string) {
    this.editor?.setValue(value);
  }

  setReadOnly(readOnly: boolean): void {
    this.editor?.updateOptions({ readOnly });
  }

  protected firstUpdated(): void {
    setupMonaco();

    const container = this.querySelector<HTMLDivElement>('.gc-editor-container')!;

    this.editor = monaco.editor.create(container, {
      value: persistence.getDataEditorContent(),
      language: 'json',
      automaticLayout: true,
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      fontSize: 13,
      fontFamily: EDITOR_FONT_FAMILY,
      fixedOverflowWidgets: true,
      tabSize: 2,
    });

    this.editor.onDidChangeModelContent(() => {
      persistence.saveDataEditorContent(this.value);
      this.dispatchEvent(
        new CustomEvent('gc-data-changed', {
          detail: { hasContent: this.value.trim().length > 0 },
          bubbles: true,
          composed: true,
        }),
      );
    });
  }

  protected render() {
    return html`<div class="gc-editor-container" aria-label="JSON data editor"></div>`;
  }
}
