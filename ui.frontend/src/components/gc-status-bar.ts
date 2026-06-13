import { html, LitElement } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { config } from '../config';
import { StoreController } from '../state/store';

const IS_MAC = /Mac|iPhone|iPad/.test(navigator.platform);
const MOD = IS_MAC ? '⌘' : 'Ctrl+';

@customElement('gc-status-bar')
export class GcStatusBar extends LitElement {
  private store = new StoreController(this);

  @state() private line = 1;
  @state() private column = 1;

  private cursorListener = ((event: CustomEvent<{ line: number; column: number }>) => {
    this.line = event.detail.line;
    this.column = event.detail.column;
  }) as EventListener;

  createRenderRoot(): this {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    window.addEventListener('gc-cursor', this.cursorListener);
  }

  disconnectedCallback(): void {
    window.removeEventListener('gc-cursor', this.cursorListener);
    super.disconnectedCallback();
  }

  protected render() {
    const { running, result } = this.store.state;
    const error = !running && !!result?.exceptionStackTrace?.length;
    const status = running ? '● Running' : error ? '✗ Error' : '✓ Ready';

    return html`
      <footer class="gc-status-bar">
        <span class="gc-status-state ${running ? 'is-running' : error ? 'is-error' : 'is-ready'}">${status}</span>
        ${config.groovyVersion ? html`<span>Groovy ${config.groovyVersion}</span>` : ''}
        <span>Ln ${this.line}, Col ${this.column}</span>
        <span class="gc-status-spacer"></span>
        <span class="gc-status-hint">${MOD}↵ Run</span>
        <span class="gc-status-hint">${MOD}S Save</span>
        <span class="gc-status-hint">${MOD}K Commands</span>
      </footer>
    `;
  }
}
