import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { config } from '@console/config';
import { persistence } from '@console/state/local-storage';
import '@console/components/gc-aem-nav';

type View =
  | { view: 'list' }
  | { view: 'run'; name: string; prefill: Record<string, string[]>; autorun: boolean }
  | { view: 'edit'; name: string | null };

/** Query-string key (in the run route hash) that requests immediate execution once the form is prefilled. */
const AUTORUN_KEY = 'autorun';

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
    // tell the lazy-loaded Monaco editor (if present) to follow, without importing it here
    window.dispatchEvent(
      new CustomEvent('gc-color-scheme-changed', { detail: { colorScheme: this.colorScheme } }),
    );
  }

  protected render() {
    return html`
      <sp-theme class="gcr-root-theme" system="spectrum" color=${this.colorScheme} scale="medium">
        <div class="gcr-frame">
          ${config.aem ? html`<gc-aem-nav></gc-aem-nav>` : nothing}
          <header class="gcr-app-bar">
            <a class="gcr-brand" href="#/">
              <span class="gcr-brand-context">Groovy Console</span>
              <span class="gcr-brand-sep">/</span>
              Reports
            </a>
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
        return html`<gcr-report-run
          .name=${this.route.name}
          .prefill=${this.route.prefill}
          .autorun=${this.route.autorun}
        ></gcr-report-run>`;
      case 'edit':
        return html`<gcr-report-editor .name=${this.route.name}></gcr-report-editor>`;
      default:
        return html`<gcr-report-list></gcr-report-list>`;
    }
  }
}

function parseHash(hash: string): View {
  const raw = hash.replace(/^#\/?/, '');
  // split off a "?a=b&c=d" query string so it doesn't leak into the path segments
  const queryIndex = raw.indexOf('?');
  const path = queryIndex >= 0 ? raw.slice(0, queryIndex) : raw;
  const query = queryIndex >= 0 ? raw.slice(queryIndex + 1) : '';
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
    if (segments[2] === 'edit') {
      return { view: 'edit', name };
    }
    const { prefill, autorun } = parseRunQuery(query);
    return { view: 'run', name, prefill, autorun };
  }
  return { view: 'list' };
}

/**
 * Parse the run route's query string into parameter prefills and the autorun flag. Any key other than the
 * reserved {@link AUTORUN_KEY} is treated as a parameter value to prefill; unknown keys are simply ignored by
 * the run view. A key may repeat (`?tag=a&tag=b`) to seed a `multiple` parameter with several values; each key's
 * occurrences are collected in order. `autorun` (or `autorun=1`/`true`) requests execution on load;
 * `autorun=0`/`false` disables it.
 */
function parseRunQuery(query: string): { prefill: Record<string, string[]>; autorun: boolean } {
  const prefill: Record<string, string[]> = {};
  let autorun = false;
  if (query) {
    for (const [key, value] of new URLSearchParams(query)) {
      if (key === AUTORUN_KEY) {
        autorun = value !== 'false' && value !== '0';
      } else {
        (prefill[key] ??= []).push(value);
      }
    }
  }
  return { prefill, autorun };
}

/** Helper for child components to surface a toast on the app root. */
export function toast(target: HTMLElement, message: string, variant: 'positive' | 'negative' = 'positive'): void {
  target.dispatchEvent(
    new CustomEvent<Toast>('gcr-toast', { detail: { message, variant }, bubbles: true, composed: true }),
  );
}
