import { html, LitElement, nothing } from 'lit';
import { customElement, property, query, state } from 'lit/decorators.js';
import { ApiError } from '@console/api/client';
import {
  deleteExecution,
  executeReport,
  exportUrl,
  getExecution,
  getExecutions,
  getReport,
  getResultPage,
  listChildPaths,
} from '../../api/reports-api';
import type { ReportDefinition, ReportExecution, ReportParameter, ResultPage } from '../../api/reports-types';
import { toast } from './gcr-app';
import { mutePlaceholders } from '@console/util/mute-placeholders';
import type { GcrPathBrowser } from './gcr-path-browser';
import type { PathType } from '../../api/reports-types';
import { formatDate, renderResultTable } from './result-cell';
import { validateRequired } from './validate-parameters';

const PAGE_SIZES = [10, 25, 50, 100];

/** Poll cadence while waiting for an asynchronous report execution to finish. Starts short so a report that
 *  completes almost immediately shows without a fixed delay, then backs off toward the max for long runs. */
const POLL_INITIAL_MS = 150;
const POLL_MAX_INTERVAL_MS = 1500;
const POLL_BACKOFF_FACTOR = 2;
/** Overall ceiling before the UI stops polling (the run keeps going server-side). */
const POLL_MAX_MS = 30 * 60 * 1000;

/** Run view: parameter form, synchronous execution, paginated result table, exports and history. */
@customElement('gcr-report-run')
export class GcrReportRun extends LitElement {
  @property() name = '';

  @state() private definition: ReportDefinition | null = null;
  @state() private loaded = false;
  @state() private error: string | null = null;
  @state() private running = false;
  @state() private values: Record<string, string> = {};
  @state() private execution: ReportExecution | null = null;
  @state() private resultPage: ResultPage | null = null;
  @state() private loadingPage = false;
  @state() private executions: ReportExecution[] = [];
  @state() private pageSize = 0;
  /** Repository-path autocomplete suggestions per PATH parameter, keyed by parameter name. */
  @state() private pathSuggestions: Record<string, string[]> = {};
  /** Validation messages for required parameters left empty, keyed by parameter name. */
  @state() private fieldErrors: Record<string, string> = {};
  /** The PATH parameter currently being picked in the path browser. */
  private browsingParam: string | null = null;

  /** Bumped whenever the viewed report changes or a new run starts, so in-flight loads/polls for a superseded
   *  report bail out instead of writing their result over the current report's state. */
  private token = 0;
  private disposed = false;
  /** Monotonic request counter per path parameter, so a slow shallow lookup can't clobber a deeper one. */
  private pathSuggestionSeq: Record<string, number> = {};

  @query('gcr-path-browser') private pathBrowser?: GcrPathBrowser;

  createRenderRoot(): this {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    this.disposed = false;
  }

  disconnectedCallback(): void {
    this.disposed = true;
    super.disconnectedCallback();
  }

  protected updated(): void {
    mutePlaceholders(this);
  }

  protected willUpdate(changed: Map<string, unknown>): void {
    if (changed.has('name') && this.name) {
      void this.load();
    }
  }

  private async load(): Promise<void> {
    const token = ++this.token;

    this.loaded = false;
    this.error = null;
    this.execution = null;
    this.resultPage = null;

    try {
      const definition = await getReport(this.name);

      if (this.stale(token)) {
        return;
      }

      this.definition = definition;
      this.pageSize = definition.pageSize ?? 0;

      const defaults: Record<string, string> = {};
      for (const parameter of definition.parameters) {
        defaults[parameter.name] = parameter.defaultValue ?? '';
      }
      this.values = defaults;

      void this.refreshExecutions();
    } catch (error) {
      if (!this.stale(token)) {
        this.error = error instanceof ApiError ? error.message : 'Could not load report.';
      }
    } finally {
      if (!this.stale(token)) {
        this.loaded = true;
      }
    }
  }

  /** True once this element was disconnected or a newer load/run superseded the given token. */
  private stale(token: number): boolean {
    return this.disposed || token !== this.token;
  }

  private async refreshExecutions(): Promise<void> {
    try {
      this.executions = (await getExecutions(this.name)).executions.filter(
        (execution) => execution.status !== 'RUNNING',
      );
    } catch {
      this.executions = [];
    }
  }

  private async run(): Promise<void> {
    if (this.running || !this.definition) {
      return;
    }

    const errors = validateRequired(this.definition.parameters, this.values);
    this.fieldErrors = errors;
    if (Object.keys(errors).length) {
      const first = this.definition.parameters.find((parameter) => errors[parameter.name]);
      if (first) {
        (this.querySelector(`#param-${CSS.escape(first.name)}`) as HTMLElement | null)?.focus();
      }
      return;
    }

    const token = ++this.token;
    this.running = true;
    this.execution = null;
    this.resultPage = null;

    try {
      // execution runs asynchronously on the server: it returns RUNNING immediately, then we poll
      let execution = await executeReport(this.name, this.values);
      if (this.stale(token)) {
        return;
      }
      this.execution = execution;

      execution = await this.awaitCompletion(execution, token);
      if (this.stale(token)) {
        return;
      }
      this.execution = execution;

      if (execution.status === 'SUCCESS') {
        await this.loadPage(execution.executionId, 1);
      } else if (execution.status === 'RUNNING') {
        toast(this, 'The report is still running — check the previous runs shortly.', 'positive');
      }
    } catch (error) {
      if (!this.stale(token)) {
        toast(this, error instanceof ApiError ? error.message : 'Report execution failed.', 'negative');
      }
    } finally {
      if (!this.stale(token)) {
        this.running = false;
        void this.refreshExecutions();
      }
    }
  }

  /** Poll a RUNNING execution until it finishes (or the poll ceiling is reached); stops if superseded. */
  private async awaitCompletion(execution: ReportExecution, token: number): Promise<ReportExecution> {
    let current = execution;
    let interval = POLL_INITIAL_MS;
    const deadline = Date.now() + POLL_MAX_MS;

    while (current.status === 'RUNNING' && Date.now() < deadline && !this.stale(token)) {
      await new Promise((resolve) => setTimeout(resolve, interval));
      interval = Math.min(interval * POLL_BACKOFF_FACTOR, POLL_MAX_INTERVAL_MS);
      try {
        const next = await getExecution(current.executionId);
        if (this.stale(token)) {
          return current;
        }
        current = next;
        this.execution = current;
      } catch {
        // transient polling error — keep waiting
      }
    }

    return current;
  }

  private async loadPage(executionId: string, page: number): Promise<void> {
    this.loadingPage = true;
    try {
      this.resultPage = await getResultPage(executionId, page, this.pageSize || undefined);
      this.pageSize = this.resultPage.pageSize;
    } catch (error) {
      toast(this, error instanceof ApiError ? error.message : 'Could not load result page.', 'negative');
    } finally {
      this.loadingPage = false;
    }
  }

  private async showExecution(executionId: string): Promise<void> {
    const execution = this.executions.find((candidate) => candidate.executionId === executionId);
    if (!execution) {
      return;
    }

    this.execution = execution;
    this.resultPage = null;

    if (execution.status === 'SUCCESS') {
      await this.loadPage(execution.executionId, 1);
    }
  }

  private async removeExecution(executionId: string): Promise<void> {
    if (!window.confirm('Delete this execution and its result?')) {
      return;
    }

    try {
      await deleteExecution(executionId);
      if (this.execution?.executionId === executionId) {
        this.execution = null;
        this.resultPage = null;
      }
      toast(this, 'Execution deleted.');
    } catch (error) {
      toast(this, error instanceof ApiError ? error.message : 'Could not delete execution.', 'negative');
    } finally {
      void this.refreshExecutions();
    }
  }

  protected render() {
    if (!this.loaded) {
      return html`<div class="gcr-loading" role="status">
        <sp-progress-circle indeterminate size="l"></sp-progress-circle>
        <span class="gcr-visually-hidden">Loading report…</span>
      </div>`;
    }

    if (this.error || !this.definition) {
      return html`<div class="gcr-empty" role="alert">${this.error ?? 'Report not found.'}</div>`;
    }

    const definition = this.definition;

    return html`
      <div
        class="gcr-page"
        @gcr-path-selected=${(event: CustomEvent<{ path: string }>) => this.onPathSelected(event)}
      >
        <gcr-path-browser></gcr-path-browser>
        <div class="gcr-breadcrumbs"><a href="#/">Reports</a> / ${definition.title}</div>

        <div class="gcr-page-header">
          <div>
            <h1>${definition.title}</h1>
            ${definition.description ? html`<p class="gcr-description">${definition.description}</p>` : nothing}
          </div>
          <div class="gcr-page-header-actions">
            ${definition.canEdit
              ? html`
                  <sp-button
                    variant="secondary"
                    href="#/report/${encodeURIComponent(definition.name)}/edit"
                  >
                    Edit
                  </sp-button>
                `
              : nothing}
          </div>
        </div>

        <section class="gcr-panel">
          ${definition.parameters.length
            ? html`<div class="gcr-form-grid">
                ${definition.parameters.map((parameter) => this.renderParameter(parameter))}
              </div>`
            : nothing}
          <div class="gcr-run-bar">
            <sp-button ?disabled=${this.running} @click=${() => void this.run()}>
              ${this.running ? 'Running…' : 'Run report'}
            </sp-button>
            ${this.running
              ? html`<sp-progress-circle indeterminate size="s"></sp-progress-circle>`
              : nothing}
            ${this.executions.length
              ? html`
                  <sp-picker
                    class="gcr-history-picker"
                    label="Previous runs"
                    placement="bottom-end"
                    value=${this.execution?.executionId ?? ''}
                    @change=${(event: Event) =>
                      void this.showExecution((event.target as HTMLInputElement).value)}
                  >
                    ${this.executions.map(
                      (execution) => html`
                        <sp-menu-item value=${execution.executionId}>
                          ${formatDate(execution.startedAt)} — ${execution.status}
                          (${execution.rowCount ?? 0} rows, ${execution.userId})
                        </sp-menu-item>
                      `,
                    )}
                  </sp-picker>
                `
              : nothing}
          </div>
        </section>

        ${this.renderExecution(definition)}
      </div>
    `;
  }

  private renderParameter(parameter: ReportParameter) {
    const value = this.values[parameter.name] ?? '';
    const update = (newValue: string): void => {
      this.values = { ...this.values, [parameter.name]: newValue };
      if (this.fieldErrors[parameter.name]) {
        const { [parameter.name]: _cleared, ...rest } = this.fieldErrors;
        this.fieldErrors = rest;
      }
    };
    const error = this.fieldErrors[parameter.name];
    const label = html`
      <sp-field-label for="param-${parameter.name}" ?required=${parameter.required}>
        ${parameter.label || parameter.name}
      </sp-field-label>
    `;
    const errorMessage = error
      ? html`<sp-help-text variant="negative" role="alert">${error}</sp-help-text>`
      : nothing;

    switch (parameter.type) {
      case 'BOOLEAN':
        return html`
          <div class="gcr-field">
            <sp-checkbox
              id="param-${parameter.name}"
              ?checked=${value === 'true'}
              @change=${(event: Event) => update((event.target as HTMLInputElement).checked ? 'true' : 'false')}
            >
              ${parameter.label || parameter.name}
            </sp-checkbox>
          </div>
        `;
      case 'SELECT':
        return html`
          <div class="gcr-field">
            ${label}
            <sp-picker
              id="param-${parameter.name}"
              value=${value}
              ?invalid=${!!error}
              @change=${(event: Event) => update((event.target as HTMLInputElement).value)}
            >
              ${parameter.options.map((option) => html`<sp-menu-item value=${option}>${option}</sp-menu-item>`)}
            </sp-picker>
            ${errorMessage}
          </div>
        `;
      case 'DATE':
        return html`
          <div class="gcr-field">
            ${label}
            <input
              id="param-${parameter.name}"
              class="gcr-date-input"
              type="date"
              .value=${value}
              @input=${(event: Event) => update((event.target as HTMLInputElement).value)}
            />
            ${errorMessage}
          </div>
        `;
      case 'NUMBER':
        return html`
          <div class="gcr-field">
            ${label}
            <sp-textfield
              id="param-${parameter.name}"
              type="number"
              value=${value}
              ?invalid=${!!error}
              @input=${(event: Event) => update((event.target as HTMLInputElement).value)}
            ></sp-textfield>
            ${errorMessage}
          </div>
        `;
      case 'PATH':
        return html`
          <div class="gcr-field">
            ${label}
            <div class="gcr-path-field">
              <input
                id="param-${parameter.name}"
                class="gcr-path-input"
                list="paths-${parameter.name}"
                .value=${value}
                placeholder=${parameter.rootPath || '/content'}
                autocomplete="off"
                @focus=${() => void this.loadPathSuggestions(parameter.name, value)}
                @input=${(event: Event) => {
                  const next = (event.target as HTMLInputElement).value;
                  update(next);
                  void this.loadPathSuggestions(parameter.name, next);
                }}
              />
              <datalist id="paths-${parameter.name}">
                ${(this.pathSuggestions[parameter.name] ?? []).map((path) => html`<option value=${path}></option>`)}
              </datalist>
              <sp-action-button title="Browse repository" @click=${() => this.openPathBrowser(parameter)}>
                Browse…
              </sp-action-button>
            </div>
            ${errorMessage}
          </div>
        `;
      default:
        return html`
          <div class="gcr-field">
            ${label}
            <sp-textfield
              id="param-${parameter.name}"
              value=${value}
              ?invalid=${!!error}
              @input=${(event: Event) => update((event.target as HTMLInputElement).value)}
            ></sp-textfield>
            ${errorMessage}
          </div>
        `;
    }
  }

  /** Open the shared path browser modal configured for the given PATH parameter. */
  private openPathBrowser(parameter: ReportParameter): void {
    if (!this.pathBrowser) {
      return;
    }
    this.browsingParam = parameter.name;
    this.pathBrowser.pathType = (parameter.pathType as PathType) || 'NODE';
    this.pathBrowser.rootPath = parameter.rootPath ?? '';
    void this.pathBrowser.openBrowser(this.values[parameter.name] ?? '');
  }

  private onPathSelected(event: CustomEvent<{ path: string }>): void {
    if (this.browsingParam) {
      const name = this.browsingParam;
      this.values = { ...this.values, [name]: event.detail.path };
      if (this.fieldErrors[name]) {
        const { [name]: _cleared, ...rest } = this.fieldErrors;
        this.fieldErrors = rest;
      }
      this.browsingParam = null;
    }
  }

  /** Fetch the children of the path segment the user is currently typing, for the datalist. */
  private async loadPathSuggestions(name: string, value: string): Promise<void> {
    // suggest siblings/children relative to the last complete "/" in the input
    const cut = value.lastIndexOf('/');
    const parent = cut > 0 ? value.slice(0, cut) : '/';

    const seq = (this.pathSuggestionSeq[name] ?? 0) + 1;
    this.pathSuggestionSeq[name] = seq;

    const children = await listChildPaths(parent);

    // ignore a response that a newer keystroke has already superseded, so a slow shallow lookup can't
    // clobber suggestions for the deeper path the user has since typed
    if (this.disposed || this.pathSuggestionSeq[name] !== seq) {
      return;
    }

    // assign so an empty level clears stale suggestions from a shallower path
    this.pathSuggestions = { ...this.pathSuggestions, [name]: children };
  }

  private renderExecution(definition: ReportDefinition) {
    const execution = this.execution;

    if (!execution) {
      return nothing;
    }

    if (execution.status === 'FAILED') {
      return html`
        <section class="gcr-panel gcr-error-panel">
          <h3>Execution failed</h3>
          ${execution.output ? html`<pre class="gcr-output">${execution.output}</pre>` : nothing}
          <pre class="gcr-stacktrace">${execution.exceptionStackTrace}</pre>
        </section>
      `;
    }

    return html`
      <section class="gcr-panel">
        <div class="gcr-result-header">
          <div class="gcr-result-summary" role="status" aria-live="polite">
            ${execution.rowCount ?? 0} rows · ${execution.runningTime ?? ''}
          </div>
          <div class="gcr-result-actions">
            ${definition.exportFormats.map(
              (format) => html`
                <sp-button
                  size="s"
                  variant="secondary"
                  href=${exportUrl(execution.executionId, format.format)}
                  download
                >
                  Download ${format.format.toUpperCase()}
                </sp-button>
              `,
            )}
            ${definition.canEdit
              ? html`
                  <sp-action-button
                    size="s"
                    quiet
                    @click=${() => void this.removeExecution(execution.executionId)}
                  >
                    Delete
                  </sp-action-button>
                `
              : nothing}
          </div>
        </div>

        ${execution.output
          ? html`
              <details class="gcr-output-details">
                <summary>Script output</summary>
                <pre class="gcr-output">${execution.output}</pre>
              </details>
            `
          : nothing}
        ${this.renderResultTable()}
      </section>
    `;
  }

  private renderResultTable() {
    if (this.loadingPage && !this.resultPage) {
      return html`<div class="gcr-loading" role="status">
        <sp-progress-circle indeterminate size="m"></sp-progress-circle>
        <span class="gcr-visually-hidden">Loading results…</span>
      </div>`;
    }

    const page = this.resultPage;

    if (!page) {
      return nothing;
    }

    if (page.totalRows === 0) {
      return html`<div class="gcr-empty">The report returned no rows.</div>`;
    }

    return html`
      ${renderResultTable(page.columns, page.rows)}

      <nav class="gcr-pagination" aria-label="Result pages">
        <span class="gcr-pagination-info">
          Page ${page.page} of ${page.totalPages} (${page.totalRows} rows)
        </span>
        <sp-action-button
          size="s"
          ?disabled=${page.previousPage < 0 || this.loadingPage}
          @click=${() => void this.loadPage(this.execution!.executionId, page.previousPage)}
        >
          ← Previous
        </sp-action-button>
        <sp-action-button
          size="s"
          ?disabled=${page.nextPage < 0 || this.loadingPage}
          @click=${() => void this.loadPage(this.execution!.executionId, page.nextPage)}
        >
          Next →
        </sp-action-button>
        <sp-picker
          class="gcr-page-size"
          label="Page size"
          value=${String(page.pageSize)}
          @change=${(event: Event) => {
            this.pageSize = Number((event.target as HTMLInputElement).value);
            void this.loadPage(this.execution!.executionId, 1);
          }}
        >
          ${pageSizeOptions(page.pageSize).map(
            (size) => html`<sp-menu-item value=${String(size)}>${size} / page</sp-menu-item>`,
          )}
        </sp-picker>
      </nav>
    `;
  }
}

function pageSizeOptions(current: number): number[] {
  return PAGE_SIZES.includes(current) ? PAGE_SIZES : [...PAGE_SIZES, current].sort((a, b) => a - b);
}

