import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { persistence } from '../state/local-storage';
import { mutePlaceholders } from '../util/mute-placeholders';

/** Save-as dialog: prompts for a file name and emits gc-save-script. */
@customElement('gc-save-dialog')
export class GcSaveDialog extends LitElement {
  @state() private open = false;
  @state() private fileName = '';

  createRenderRoot(): this {
    return this;
  }

  protected updated(): void {
    mutePlaceholders(this);
  }

  show(): void {
    this.fileName = persistence.getScriptName() || '';
    this.open = true;
  }

  private confirm(): void {
    let fileName = this.fileName.trim();

    if (!fileName) {
      return;
    }

    if (!fileName.endsWith('.groovy')) {
      fileName = `${fileName}.groovy`;
    }

    this.open = false;
    this.dispatchEvent(
      new CustomEvent('gc-save-script', { detail: { fileName }, bubbles: true, composed: true }),
    );
  }

  protected render() {
    if (!this.open) {
      return nothing;
    }

    return html`
      <sp-dialog-wrapper
        open
        underlay
        headline="Save Script"
        confirm-label="Save"
        cancel-label="Cancel"
        @confirm=${this.confirm}
        @cancel=${() => (this.open = false)}
        @close=${() => (this.open = false)}
      >
        <sp-field-label for="gc-save-file-name">File Name</sp-field-label>
        <sp-textfield
          id="gc-save-file-name"
          placeholder="script.groovy"
          .value=${this.fileName}
          @input=${(event: Event) => (this.fileName = (event.target as HTMLInputElement).value)}
          @keydown=${(event: KeyboardEvent) => event.key === 'Enter' && this.confirm()}
        ></sp-textfield>
      </sp-dialog-wrapper>
    `;
  }
}
