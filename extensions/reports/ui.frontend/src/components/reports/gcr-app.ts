import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { persistence } from '@console/state/local-storage';

type View = { view: 'list' } | { view: 'run'; name: string } | { view: 'edit'; name: string | null };

interface Toast {
  message: string;
  variant: 'positive' | 'negative';
}

/**
 * Root of the business-facing reports UI (served at /apps/groovyconsole.reports.html).
 * Hash routing: #/ (list), #/report/<name> (run), #/report/<name>/edit, #/new (create).
 */
@customElement('gcr-app')
export class GcrApp extends LitElement {
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

    this.addEventListener('gcr-toast', ((event: CustomEvent<Toast>) => {
      this.toast = event.detail;
    }) as EventListener);
    this.addEventListener('gcr-navigate', ((event: CustomEvent<{ hash: string }>) => {
      window.location.hash = event.detail.hash;
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
      <sp-theme class="gcr-root-theme" system="spectrum" color=${this.colorScheme} scale="medium">
        <div class="gcr-frame">
          <header class="gcr-app-bar">
            <a class="gcr-brand" href="#/">Reports</a>
            <div class="gcr-app-bar-actions">
              <sp-switch
                ?checked=${this.colorScheme === 'dark'}
                @change=${() => this.toggleColorScheme()}
                size="s"
              >
                Dark
              </sp-switch>
            </div>
          </header>

          <main class="gcr-main">${this.renderView()}</main>

          <!-- mirror toast messages into a persistent live region so screen readers always announce them -->
          <div class="gcr-visually-hidden" role="status" aria-live="polite">${this.toast?.message ?? ''}</div>

          ${this.toast
            ? html`
                <sp-toast
                  class="gcr-toast"
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
        return html`<gcr-report-run .name=${this.route.name}></gcr-report-run>`;
      case 'edit':
        return html`<gcr-report-editor .name=${this.route.name}></gcr-report-editor>`;
      default:
        return html`<gcr-report-list></gcr-report-list>`;
    }
  }
}

function parseHash(hash: string): View {
  const path = hash.replace(/^#\/?/, '');
  const segments = path.split('/').filter(Boolean);

  if (segments[0] === 'new') {
    return { view: 'edit', name: null };
  }
  if (segments[0] === 'report' && segments[1]) {
    // a malformed escape (e.g. "#/report/%") would throw URIError and dead-end routing; fall back to raw
    let name: string;
    try {
      name = decodeURIComponent(segments[1]);
    } catch {
      name = segments[1];
    }
    return segments[2] === 'edit' ? { view: 'edit', name } : { view: 'run', name };
  }
  return { view: 'list' };
}

/** Helper for child components to surface a toast on the app root. */
export function toast(target: HTMLElement, message: string, variant: 'positive' | 'negative' = 'positive'): void {
  target.dispatchEvent(
    new CustomEvent<Toast>('gcr-toast', { detail: { message, variant }, bubbles: true, composed: true }),
  );
}
