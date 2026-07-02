import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { ApiError } from '@console/api/client';
import { dryRunMigrations, getRegistry, getRun, getRuns, queueMigrations } from '../../api/migration-api';
import type { MigrationRunSummary, MigrationScriptState } from '../../api/migration-types';
import { statusVariant } from './migration-status';
import { toast } from './gcm-app';

const POLL_INTERVAL_MS = 2000;

/** List view: migration run history and per-script registry state, with run/dry-run actions. */
@customElement('gcm-run-list')
export class GcmRunList extends LitElement {
  @state() private runs: MigrationRunSummary[] = [];
  @state() private registry: MigrationScriptState[] = [];
  @state() private running = false;
  @state() private triggering = false;
  @state() private loaded = false;
  @state() private error: string | null = null;

  private disposed = false;

  createRenderRoot(): this {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    this.disposed = false;
    void this.refresh();
  }

  disconnectedCallback(): void {
    this.disposed = true;
    super.disconnectedCallback();
  }

  private async refresh(): Promise<void> {
    try {
      const [runs, registry] = await Promise.all([getRuns(), getRegistry()]);
      this.runs = runs.data;
      this.running = runs.running;
      this.registry = registry.data;
      this.error = null;
    } catch {
      this.error = 'Could not load the migration history.';
    } finally {
      this.loaded = true;
    }
  }

  private get pendingCount(): number {
    return this.registry.filter((state) => state.pending).length;
  }

  private async triggerRun(): Promise<void> {
    this.triggering = true;
    try {
      const queued = await queueMigrations();
      toast(this, `Migration run ${queued.runId} started.`);

      let status = queued.status;
      while (status === 'RUNNING' && !this.disposed) {
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
        status = (await getRun(queued.runId)).status;
      }

      if (status === 'SUCCESS') {
        toast(this, 'Migration run finished successfully.');
      } else if (status === 'FAILED') {
        toast(this, 'Migration run failed. Open the run for details.', 'negative');
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        toast(this, 'A migration run is already in progress.', 'negative');
      } else {
        toast(this, 'Could not trigger the migration run.', 'negative');
      }
    } finally {
      this.triggering = false;
      void this.refresh();
    }
  }

  private async dryRun(): Promise<void> {
    try {
      const run = await dryRunMigrations();
      toast(this, `Dry run: ${run.pending} script(s) pending.`);
      void this.refresh();
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        toast(this, 'A migration run is already in progress.', 'negative');
      } else {
        toast(this, 'Could not perform the dry run.', 'negative');
      }
    }
  }

  protected render() {
    if (!this.loaded) {
      return html`<div class="gcm-page"><sp-progress-circle indeterminate size="l"></sp-progress-circle></div>`;
    }

    if (this.error) {
      return html`<div class="gcm-page"><p class="gcm-empty">${this.error}</p></div>`;
    }

    return html`
      <div class="gcm-page">
        <div class="gcm-page-header">
          <h2>Run history</h2>
          <div class="gcm-page-actions">
            <sp-action-button size="s" quiet @click=${() => void this.refresh()} title="Refresh">↻</sp-action-button>
            <sp-button size="s" variant="secondary" @click=${() => void this.dryRun()}>Dry run</sp-button>
            <sp-button
              size="s"
              variant="primary"
              ?disabled=${this.triggering || this.running || this.pendingCount === 0}
              @click=${() => void this.triggerRun()}
            >
              ${this.triggering || this.running ? 'Running…' : `Run migrations (${this.pendingCount} pending)`}
            </sp-button>
          </div>
        </div>

        ${this.runs.length === 0
          ? html`<p class="gcm-empty">No migration runs yet.</p>`
          : html`
              <div class="gcm-table-wrapper">
                <table class="gcm-table">
                  <thead>
                    <tr>
                      <th>Started</th>
                      <th>Status</th>
                      <th>Trigger</th>
                      <th>Duration</th>
                      <th class="gcm-cell-number">Executed</th>
                      <th class="gcm-cell-number">Failed</th>
                      <th class="gcm-cell-number">Skipped</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    ${this.runs.map(
                      (run) => html`
                        <tr>
                          <td>${run.startDate}</td>
                          <td><sp-badge size="s" variant=${statusVariant(run.status)}>${run.status}</sp-badge></td>
                          <td>${run.trigger}</td>
                          <td>${run.runningTime}</td>
                          <td class="gcm-cell-number">${run.executed}</td>
                          <td class="gcm-cell-number">${run.failed}</td>
                          <td class="gcm-cell-number">${run.skipped}</td>
                          <td><a href=${`#/run/${encodeURIComponent(run.runId)}`}>Details</a></td>
                        </tr>
                      `,
                    )}
                  </tbody>
                </table>
              </div>
            `}

        <div class="gcm-page-header">
          <h2>Scripts</h2>
        </div>

        ${this.registry.length === 0
          ? html`<p class="gcm-empty">No migration scripts deployed below /conf/groovyconsole/scripts/migration.</p>`
          : html`
              <div class="gcm-table-wrapper">
                <table class="gcm-table">
                  <thead>
                    <tr>
                      <th>Script</th>
                      <th>Status</th>
                      <th>Last run</th>
                      <th>Duration</th>
                      <th>Pending</th>
                      <th>Always</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${this.registry.map(
                      (state) => html`
                        <tr>
                          <td class="gcm-cell-script" title=${state.scriptPath}>${state.scriptPath}</td>
                          <td>
                            ${state.status
                              ? html`<sp-badge size="s" variant=${statusVariant(state.status)}>${state.status}</sp-badge>`
                              : html`<span class="gcm-cell-empty">never run</span>`}
                          </td>
                          <td>${state.lastRunDate || nothing}</td>
                          <td>${state.runningTime || nothing}</td>
                          <td>${state.pending ? 'yes' : 'no'}</td>
                          <td>${state.always ? 'yes' : 'no'}</td>
                        </tr>
                      `,
                    )}
                  </tbody>
                </table>
              </div>
            `}
      </div>
    `;
  }
}
