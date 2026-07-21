import { css, html, LitElement } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { ApiError } from '@console/api/client';
import { getPending, getRun, getRuns, migrationsPageUrl, queueMigrations } from '../../api/migration-api';
import type { MigrationRunSummary } from '../../api/migration-types';

const POLL_INTERVAL_MS = 2000;

/**
 * Developer-focused migration panel, registered in the console's activity rail by the migration UI
 * extension module (migration-console-panel.ts).
 *
 * Self-contained (shadow DOM + own styles) and talks to the console shell only via the documented
 * extension events (`gc-toast`).
 */
@customElement('gc-migration')
export class GcMigration extends LitElement {
  static styles = css`
    :host {
      display: block;
      font-size: 13px;
    }

    .toolbar {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;
    }

    .toolbar .spacer {
      flex: 1;
    }

    .section-title {
      font-weight: 600;
      margin: 12px 0 6px;
    }

    .empty {
      color: var(--spectrum-gray-600);
    }

    ul {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
    }

    li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 8px 4px;
      border-bottom: 1px solid var(--spectrum-gray-200);
    }

    .script-path {
      font-family: var(--spectrum-code-font-family-stack, monospace);
      font-size: 12px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      direction: rtl;
      text-align: left;
    }

    .run-meta {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
    }

    .run-date {
      font-size: 12px;
      color: var(--spectrum-gray-600);
    }
  `;

  @state() private pendingScripts: string[] = [];
  @state() private lastRuns: MigrationRunSummary[] = [];
  @state() private running = false;
  @state() private triggering = false;
  @state() private loaded = false;
  @state() private error: string | null = null;

  private disposed = false;

  connectedCallback(): void {
    super.connectedCallback();
    this.disposed = false;
    void this.refresh();
  }

  disconnectedCallback(): void {
    this.disposed = true;
    super.disconnectedCallback();
  }

  private toast(message: string, variant: 'positive' | 'negative' = 'positive'): void {
    this.dispatchEvent(
      new CustomEvent('gc-toast', { detail: { message, variant }, bubbles: true, composed: true }),
    );
  }

  private async refresh(): Promise<void> {
    try {
      const [pending, runs] = await Promise.all([getPending(), getRuns()]);
      this.pendingScripts = pending.data;
      this.lastRuns = runs.data.slice(0, 5);
      this.running = runs.running;
      this.error = null;
    } catch {
      this.error = 'Could not load migrations. Is the migration extension installed?';
    } finally {
      this.loaded = true;
    }
  }

  /** Enqueue an asynchronous run and poll until it reaches a terminal status. */
  private async triggerRun(): Promise<void> {
    this.triggering = true;
    try {
      const queued = await queueMigrations();
      this.toast(`Migration run ${queued.runId} started.`);

      let status = queued.status;
      while (status === 'RUNNING' && !this.disposed) {
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
        status = (await getRun(queued.runId)).status;
      }

      if (status === 'SUCCESS') {
        this.toast('Migration run finished successfully.');
      } else if (status === 'FAILED') {
        this.toast('Migration run failed. Check the run history for details.', 'negative');
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        this.toast('A migration run is already in progress.', 'negative');
      } else {
        this.toast('Could not trigger the migration run.', 'negative');
      }
    } finally {
      this.triggering = false;
      void this.refresh();
    }
  }

  private statusVariant(status: string): 'positive' | 'negative' | 'informative' | 'neutral' {
    switch (status) {
      case 'SUCCESS':
        return 'positive';
      case 'FAILED':
        return 'negative';
      case 'RUNNING':
        return 'informative';
      default:
        return 'neutral';
    }
  }

  protected render() {
    if (!this.loaded) {
      return html`<sp-progress-circle indeterminate size="m"></sp-progress-circle>`;
    }

    if (this.error) {
      return html`<p class="empty">${this.error}</p>`;
    }

    return html`
      <div class="toolbar">
        <sp-button
          size="s"
          variant="primary"
          ?disabled=${this.triggering || this.running || this.pendingScripts.length === 0}
          @click=${() => void this.triggerRun()}
        >
          ${this.triggering || this.running ? 'Running…' : 'Run migrations'}
        </sp-button>
        <span class="spacer"></span>
        <sp-action-button size="s" quiet @click=${() => void this.refresh()} title="Refresh" aria-label="Refresh">
          <sp-icon-refresh slot="icon"></sp-icon-refresh>
        </sp-action-button>
        <sp-button size="s" variant="secondary" href=${migrationsPageUrl()} target="_blank">
          History
        </sp-button>
      </div>

      <div class="section-title">Pending scripts</div>
      ${this.pendingScripts.length === 0
        ? html`<p class="empty">No pending migration scripts.</p>`
        : html`
            <ul>
              ${this.pendingScripts.map(
                (path) => html`<li><span class="script-path" title=${path}>${path}</span></li>`,
              )}
            </ul>
          `}

      <div class="section-title">Recent runs</div>
      ${this.lastRuns.length === 0
        ? html`<p class="empty">No migration runs yet.</p>`
        : html`
            <ul>
              ${this.lastRuns.map(
                (run) => html`
                  <li>
                    <div class="run-meta">
                      <span class="run-date">${run.startDate} · ${run.trigger}</span>
                      <span>
                        ${run.executed} executed${run.failed ? `, ${run.failed} failed` : ''}${run.skipped
                          ? `, ${run.skipped} skipped`
                          : ''}
                      </span>
                    </div>
                    <sp-badge size="s" variant=${this.statusVariant(run.status)}>${run.status}</sp-badge>
                  </li>
                `,
              )}
            </ul>
          `}
    `;
  }
}
