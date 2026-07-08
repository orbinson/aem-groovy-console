import { html, LitElement, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { getRun } from '../../api/migration-api';
import type { MigrationRun } from '../../api/migration-types';
import { statusVariant } from './migration-status';

/** Detail view: aggregate run information and the per-script results table. */
@customElement('gcm-run-detail')
export class GcmRunDetail extends LitElement {
  @property() runId = '';

  @state() private run: MigrationRun | null = null;
  @state() private loaded = false;
  @state() private error: string | null = null;

  createRenderRoot(): this {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    void this.load();
  }

  protected willUpdate(changed: Map<string, unknown>): void {
    if (changed.has('runId') && this.loaded) {
      void this.load();
    }
  }

  private async load(): Promise<void> {
    this.loaded = false;
    try {
      this.run = await getRun(this.runId);
      this.error = null;
    } catch {
      this.error = `Could not load migration run ${this.runId}.`;
    } finally {
      this.loaded = true;
    }
  }

  protected render() {
    if (!this.loaded) {
      return html`<div class="gcm-page"><sp-progress-circle indeterminate size="l"></sp-progress-circle></div>`;
    }

    if (this.error || !this.run) {
      return html`
        <div class="gcm-page">
          <div class="gcm-breadcrumbs"><a href="#/">Run history</a></div>
          <p class="gcm-empty">${this.error ?? 'Run not found.'}</p>
        </div>
      `;
    }

    const run = this.run;

    return html`
      <div class="gcm-page">
        <div class="gcm-breadcrumbs"><a href="#/">Run history</a> / ${run.runId}</div>

        <div class="gcm-page-header">
          <h2>Migration run</h2>
          <sp-badge size="m" variant=${statusVariant(run.status)}>${run.status}</sp-badge>
        </div>

        <dl class="gcm-meta">
          <dt>Started</dt>
          <dd>${run.startDate}</dd>
          <dt>Finished</dt>
          <dd>${run.endDate || '—'}</dd>
          <dt>Duration</dt>
          <dd>${run.runningTime || '—'}</dd>
          <dt>Trigger</dt>
          <dd>${run.trigger}</dd>
          <dt>Scripts</dt>
          <dd>${run.executed} executed, ${run.failed} failed, ${run.skipped} skipped</dd>
        </dl>

        ${run.error ? html`<pre class="gcm-output gcm-output-error">${run.error}</pre>` : nothing}

        ${run.results.length === 0
          ? html`<p class="gcm-empty">No scripts were executed in this run.</p>`
          : html`
              <div class="gcm-table-wrapper">
                <table class="gcm-table">
                  <thead>
                    <tr>
                      <th>Script</th>
                      <th>Status</th>
                      <th>Duration</th>
                      <th>Output</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${run.results.map(
                      (result) => html`
                        <tr>
                          <td class="gcm-cell-script" title=${result.scriptPath}>${result.scriptPath}</td>
                          <td>
                            <sp-badge size="s" variant=${statusVariant(result.status)}>${result.status}</sp-badge>
                          </td>
                          <td>${result.runningTime || nothing}</td>
                          <td class="gcm-cell-output">
                            ${result.error
                              ? html`<pre class="gcm-output gcm-output-error">${result.error}</pre>`
                              : nothing}
                            ${result.output ? html`<pre class="gcm-output">${result.output}</pre>` : nothing}
                            ${!result.error && !result.output ? html`<span class="gcm-cell-empty">—</span>` : nothing}
                          </td>
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
