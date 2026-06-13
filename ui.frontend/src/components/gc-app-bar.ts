import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { config } from '../config';
import { StoreController } from '../state/store';

/** Top app bar: brand, current script name, primary Run action, theme switch, and a file menu. */
@customElement('gc-app-bar')
export class GcAppBar extends LitElement {
  private store = new StoreController(this);

  @state() private menuOpen = false;

  private outsideClickListener = (event: MouseEvent): void => {
    if (this.menuOpen && !this.contains(event.target as Node)) {
      this.menuOpen = false;
    }
  };

  createRenderRoot(): this {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    window.addEventListener('click', this.outsideClickListener);
  }

  disconnectedCallback(): void {
    window.removeEventListener('click', this.outsideClickListener);
    super.disconnectedCallback();
  }

  private emit(name: string): void {
    this.menuOpen = false;
    this.dispatchEvent(new CustomEvent(name, { bubbles: true, composed: true }));
  }

  protected render() {
    const { running, scriptName, dirty, colorScheme } = this.store.state;

    return html`
      <header class="gc-app-bar">
        <span class="gc-brand">Groovy Console</span>
        <span class="gc-script-name" title=${dirty ? 'Unsaved changes' : ''}>
          ${scriptName || 'untitled'}${dirty ? html`<span class="gc-dirty-dot" aria-label="unsaved">●</span>` : nothing}
        </span>

        <div class="gc-app-bar-actions">
          <sp-button variant="accent" size="m" ?disabled=${running} @click=${() => this.emit('gc-run')}>
            ${running
              ? html`<sp-progress-circle indeterminate size="s" slot="icon"></sp-progress-circle> Running…`
              : 'Run'}
          </sp-button>
          ${config.aem && config.distributedExecutionEnabled
            ? html`
                <sp-button variant="primary" size="m" treatment="outline" ?disabled=${running}
                           @click=${() => this.emit('gc-distribute')}>
                  Run on publish
                </sp-button>
              `
            : nothing}

          <div class="gc-overflow">
            <sp-action-button
              quiet
              aria-label="File actions"
              aria-haspopup="true"
              aria-expanded=${this.menuOpen}
              @click=${(event: Event) => {
                event.stopPropagation();
                this.menuOpen = !this.menuOpen;
              }}
            >
              File ▾
            </sp-action-button>
            ${this.menuOpen
              ? html`
                  <div class="gc-overflow-menu" role="menu">
                    <button role="menuitem" @click=${() => this.emit('gc-new')}>New</button>
                    <button role="menuitem" @click=${() => this.emit('gc-open')}>Open…</button>
                    <button role="menuitem" @click=${() => this.emit('gc-save')}>Save…</button>
                    <button role="menuitem" @click=${() => this.emit('gc-download')}>Download</button>
                  </div>
                `
              : nothing}
          </div>

          <sp-switch ?checked=${colorScheme === 'dark'} @change=${() => this.emit('gc-toggle-theme')}>
            Dark
          </sp-switch>
        </div>
      </header>
    `;
  }
}
