import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { persistence } from '@console/state/local-storage';

type View = { view: 'list' } | { view: 'run'; runId: string };

interface Toast {
  message: string;
  variant: 'positive' | 'negative';
}

/**
 * Root of the migration history UI (served at /apps/groovyconsole/migrations.html).
 * Hash routing: #/ (run history + script registry), #/run/<runId> (run detail).
 */
@customElement('gcm-app')
export class GcmApp extends LitElement {
  @state() private route: View = { view: 'list' };
  @state() private colorScheme: 'light' | 'dark' = persistence.getColorScheme();
  @state() private toast: Toast | null = null;

  createRenderRoot(): this {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    this.route = parseHash(window.location.hash);
    window.addEventListener('hashchange', this.onHashChange);

    this.addEventListener('gcm-toast', ((event: CustomEvent<Toast>) => {
      this.toast = event.detail;
    }) as EventListener);
  }

  disconnectedCallback(): void {
    window.removeEventListener('hashchange', this.onHashChange);
    super.disconnectedCallback();
  }

  private onHashChange = (): void => {
    this.route = parseHash(window.location.hash);
  };

  private toggleColorScheme(): void {
    this.colorScheme = this.colorScheme === 'dark' ? 'light' : 'dark';
    persistence.saveColorScheme(this.colorScheme);
  }

  protected render() {
    return html`
      <sp-theme class="gcm-root-theme" system="spectrum" color=${this.colorScheme} scale="medium">
        <div class="gcm-frame">
          <header class="gcm-app-bar">
            <a class="gcm-brand" href="#/">Migrations</a>
            <div class="gcm-app-bar-actions">
              <sp-switch
                ?checked=${this.colorScheme === 'dark'}
                @change=${() => this.toggleColorScheme()}
                size="s"
              >
                Dark
              </sp-switch>
            </div>
          </header>

          <main class="gcm-main">${this.renderView()}</main>

          ${this.toast
            ? html`
                <sp-toast
                  class="gcm-toast"
                  open
                  variant=${this.toast.variant}
                  timeout="5000"
                  @close=${() => (this.toast = null)}
                >
                  ${this.toast.message}
                </sp-toast>
              `
            : nothing}
        </div>
      </sp-theme>
    `;
  }

  private renderView() {
    switch (this.route.view) {
      case 'run':
        return html`<gcm-run-detail .runId=${this.route.runId}></gcm-run-detail>`;
      default:
        return html`<gcm-run-list></gcm-run-list>`;
    }
  }
}

function parseHash(hash: string): View {
  const path = hash.replace(/^#\/?/, '');
  const segments = path.split('/').filter(Boolean);

  if (segments[0] === 'run' && segments[1]) {
    return { view: 'run', runId: decodeURIComponent(segments[1]) };
  }
  return { view: 'list' };
}

/** Helper for child components to surface a toast on the app root. */
export function toast(target: HTMLElement, message: string, variant: 'positive' | 'negative' = 'positive'): void {
  target.dispatchEvent(
    new CustomEvent<Toast>('gcm-toast', { detail: { message, variant }, bubbles: true, composed: true }),
  );
}
