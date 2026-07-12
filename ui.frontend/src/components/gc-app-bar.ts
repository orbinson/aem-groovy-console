import { html, LitElement, nothing } from 'lit';
import { customElement } from 'lit/decorators.js';
import { config } from '../config';
import { StoreController } from '../state/store';

/** Top app bar: brand, current script name, primary Run action, theme switch, and a file menu. */
@customElement('gc-app-bar')
export class GcAppBar extends LitElement {
  private store = new StoreController(this);

  createRenderRoot(): this {
    return this;
  }

  private emit(name: string): void {
    this.dispatchEvent(new CustomEvent(name, { bubbles: true, composed: true }));
  }

  private onMenuChange(event: Event): void {
    const menu = event.target as HTMLElement & { value: string };
    const action = menu.value;
    menu.value = '';
    if (action) {
      this.emit(action);
    }
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

          <sp-action-menu quiet label="File actions" value="" @change=${this.onMenuChange}>
            <span slot="label-only">File</span>
            <sp-menu-item value="gc-new">New</sp-menu-item>
            <sp-menu-item value="gc-open">Open…</sp-menu-item>
            <sp-menu-item value="gc-save">Save…</sp-menu-item>
            <sp-menu-divider></sp-menu-divider>
            <sp-menu-item value="gc-download">Download</sp-menu-item>
          </sp-action-menu>

          <sp-switch ?checked=${colorScheme === 'dark'} @change=${() => this.emit('gc-toggle-theme')}>
            Dark
          </sp-switch>
        </div>
      </header>
    `;
  }
}
