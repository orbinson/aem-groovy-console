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
  resolveDynamicOptions,
} from '../../api/reports-api';
import type {
  DynamicOption,
  ReportDefinition,
  ReportExecution,
  ReportParameter,
  ReportParameterValue,
  ResultPage,
} from '../../api/reports-types';
import { toast } from './gcr-app';
import { mutePlaceholders } from '@console/util/mute-placeholders';
import type { GcrPathBrowser } from './gcr-path-browser';
import type { BrowseType } from '../../api/reports-types';
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
  @state() private values: Record<string, ReportParameterValue> = {};
  @state() private execution: ReportExecution | null = null;
  @state() private resultPage: ResultPage | null = null;
  @state() private loadingPage = false;
  @state() private executions: ReportExecution[] = [];
  @state() private pageSize = 0;
  /** Repository-path autocomplete suggestions per PATH parameter, keyed by parameter name. */
  @state() private pathSuggestions: Record<string, string[]> = {};
  /** Validation messages for required parameters left empty, keyed by parameter name. */
  @state() private fieldErrors: Record<string, string> = {};
  /** Resolved options for DYNAMIC parameters, keyed by parameter name. */
  @state() private dynamicOptions: Record<string, DynamicOption[]> = {};
  /** Whether a DYNAMIC parameter's options are being (re)loaded, keyed by parameter name. */
  @state() private dynamicLoading: Record<string, boolean> = {};
  /** Error resolving a DYNAMIC parameter's options, keyed by parameter name. */
  @state() private dynamicErrors: Record<string, string> = {};
  /** The PATH/TAG parameter entry currently being picked in the browser. */
  private browsing: { name: string; index: number | null; isTag: boolean } | null = null;

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

      const defaults: Record<string, ReportParameterValue> = {};
      for (const parameter of definition.parameters) {
        const fallback = parameter.defaultValue ?? '';
        defaults[parameter.name] = parameter.multiple ? (fallback ? [fallback] : []) : fallback;
      }
      this.values = defaults;
      this.dynamicOptions = {};
      this.dynamicErrors = {};

      // eagerly resolve dynamic options (with default values) so the pickers are populated on first open
      for (const parameter of definition.parameters) {
        if (parameter.type === 'DYNAMIC') {
          void this.loadDynamicOptions(parameter);
        }
      }

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
        @gcr-path-selected=${(event: CustomEvent<{ path: string; id?: string | null }>) =>
          this.onPathSelected(event)}
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
    const error = this.fieldErrors[parameter.name];
    const label = html`
      <sp-field-label for="param-${parameter.name}" ?required=${parameter.required}>
        ${parameter.label || parameter.name}
      </sp-field-label>
    `;
    const errorMessage = error
      ? html`<sp-help-text variant="negative" role="alert">${error}</sp-help-text>`
      : nothing;

    if (parameter.multiple) {
      const entries = this.valueList(parameter.name);

      return html`
        <div class="gcr-field">
          ${label}
          <div class="gcr-multifield">
            ${entries.map(
              (entry, index) => html`
                <div class="gcr-multifield-row">
                  ${this.renderControl(
                    parameter,
                    index === 0 ? `param-${parameter.name}` : `param-${parameter.name}-${index}`,
                    entry,
                    !!error,
                    index,
                  )}
                  <sp-action-button
                    quiet
                    size="s"
                    class="gcr-multifield-remove"
                    label="Remove value"
                    title="Remove value"
                    ?disabled=${entries.length === 1 && !entry}
                    @click=${() => this.removeValue(parameter.name, index)}
                  >
                    <sp-icon-close slot="icon"></sp-icon-close>
                  </sp-action-button>
                </div>
              `,
            )}
          </div>
          <sp-action-button quiet size="s" class="gcr-multifield-add" @click=${() => this.addValue(parameter.name)}>
            <sp-icon-add slot="icon"></sp-icon-add>
            Add value
          </sp-action-button>
          ${errorMessage}
        </div>
      `;
    }

    // BOOLEAN renders its own inline label rather than a field label
    if (parameter.type === 'BOOLEAN') {
      return html`
        <div class="gcr-field">
          ${this.renderControl(parameter, `param-${parameter.name}`, this.valueScalar(parameter.name), !!error, null)}
        </div>
      `;
    }

    return html`
      <div class="gcr-field">
        ${label}
        ${this.renderControl(parameter, `param-${parameter.name}`, this.valueScalar(parameter.name), !!error, null)}
        ${errorMessage}
      </div>
    `;
  }

  /** Render a single input control for a parameter entry (index is null for a single-value parameter). */
  private renderControl(
    parameter: ReportParameter,
    id: string,
    value: string,
    invalid: boolean,
    index: number | null,
  ) {
    const onChange = (newValue: string): void => this.setValue(parameter.name, index, newValue);

    switch (parameter.type) {
      case 'BOOLEAN':
        return html`
          <sp-checkbox
            id=${id}
            ?checked=${value === 'true'}
            @change=${(event: Event) => onChange((event.target as HTMLInputElement).checked ? 'true' : 'false')}
          >
            ${parameter.label || parameter.name}
          </sp-checkbox>
        `;
      case 'SELECT':
        return html`
          <sp-picker
            id=${id}
            value=${value}
            ?invalid=${invalid}
            @change=${(event: Event) => onChange((event.target as HTMLInputElement).value)}
          >
            ${parameter.options.map((option) => html`<sp-menu-item value=${option}>${option}</sp-menu-item>`)}
          </sp-picker>
        `;
      case 'DATE':
        return html`
          <input
            id=${id}
            class="gcr-date-input"
            type="date"
            .value=${value}
            @input=${(event: Event) => onChange((event.target as HTMLInputElement).value)}
          />
        `;
      case 'NUMBER':
        return html`
          <sp-textfield
            id=${id}
            type="number"
            value=${value}
            ?invalid=${invalid}
            @input=${(event: Event) => onChange((event.target as HTMLInputElement).value)}
          ></sp-textfield>
        `;
      case 'PATH':
        return this.renderPathControl(parameter, id, value, index);
      case 'TAG':
        return this.renderTagControl(parameter, id, value, index);
      case 'DYNAMIC':
        return this.renderDynamicControl(parameter, id, value, invalid, index);
      default:
        return html`
          <sp-textfield
            id=${id}
            value=${value}
            ?invalid=${invalid}
            @input=${(event: Event) => onChange((event.target as HTMLInputElement).value)}
          ></sp-textfield>
        `;
    }
  }

  private renderPathControl(parameter: ReportParameter, id: string, value: string, index: number | null) {
    return html`
      <div class="gcr-path-field">
        <input
          id=${id}
          class="gcr-path-input"
          list="paths-${id}"
          .value=${value}
          placeholder=${parameter.rootPath || '/content'}
          autocomplete="off"
          @focus=${() => void this.loadPathSuggestions(parameter.name, value)}
          @input=${(event: Event) => {
            const next = (event.target as HTMLInputElement).value;
            this.setValue(parameter.name, index, next);
            void this.loadPathSuggestions(parameter.name, next);
          }}
        />
        <datalist id="paths-${id}">
          ${(this.pathSuggestions[parameter.name] ?? []).map((path) => html`<option value=${path}></option>`)}
        </datalist>
        <sp-action-button title="Browse repository" @click=${() => this.openBrowser(parameter, index)}>
          Browse…
        </sp-action-button>
      </div>
    `;
  }

  private renderTagControl(parameter: ReportParameter, id: string, value: string, index: number | null) {
    return html`
      <div class="gcr-path-field">
        <sp-textfield
          id=${id}
          value=${value}
          placeholder="namespace:path/to/tag"
          @input=${(event: Event) => this.setValue(parameter.name, index, (event.target as HTMLInputElement).value)}
        ></sp-textfield>
        <sp-action-button title="Browse tags" @click=${() => this.openBrowser(parameter, index)}>
          Browse tags…
        </sp-action-button>
      </div>
    `;
  }

  private renderDynamicControl(
    parameter: ReportParameter,
    id: string,
    value: string,
    invalid: boolean,
    index: number | null,
  ) {
    const options = this.dynamicOptions[parameter.name] ?? [];
    const errorText = this.dynamicErrors[parameter.name];

    return html`
      <sp-picker
        id=${id}
        value=${value}
        ?invalid=${invalid}
        ?pending=${this.dynamicLoading[parameter.name]}
        @mousedown=${() => void this.loadDynamicOptions(parameter)}
        @change=${(event: Event) => this.setValue(parameter.name, index, (event.target as HTMLInputElement).value)}
      >
        ${options.map((option) => html`<sp-menu-item value=${option.value}>${option.label}</sp-menu-item>`)}
      </sp-picker>
      ${errorText ? html`<sp-help-text variant="negative" role="alert">${errorText}</sp-help-text>` : nothing}
    `;
  }

  // value helpers: a parameter's value is a scalar, or an array for a `multiple` parameter

  private valueList(name: string): string[] {
    const value = this.values[name];
    if (Array.isArray(value)) {
      return value.length ? value : [''];
    }
    return value ? [value] : [''];
  }

  private valueScalar(name: string): string {
    const value = this.values[name];
    return Array.isArray(value) ? (value[0] ?? '') : (value ?? '');
  }

  private clearFieldError(name: string): void {
    if (this.fieldErrors[name]) {
      const { [name]: _cleared, ...rest } = this.fieldErrors;
      this.fieldErrors = rest;
    }
  }

  private setValue(name: string, index: number | null, newValue: string): void {
    if (index === null) {
      this.values = { ...this.values, [name]: newValue };
    } else {
      const next = [...this.valueList(name)];
      next[index] = newValue;
      this.values = { ...this.values, [name]: next };
    }
    this.clearFieldError(name);
  }

  private addValue(name: string): void {
    this.values = { ...this.values, [name]: [...this.valueList(name), ''] };
  }

  private removeValue(name: string, index: number): void {
    const next = this.valueList(name).filter((_, entryIndex) => entryIndex !== index);
    this.values = { ...this.values, [name]: next.length ? next : [''] };
    this.clearFieldError(name);
  }

  /** Resolve (or refresh) the options of a DYNAMIC parameter, passing the values it may depend on. */
  private async loadDynamicOptions(parameter: ReportParameter): Promise<void> {
    const name = parameter.name;
    this.dynamicLoading = { ...this.dynamicLoading, [name]: true };
    this.dynamicErrors = { ...this.dynamicErrors, [name]: '' };

    try {
      const options = await resolveDynamicOptions(this.name, name, this.values);
      if (this.disposed) {
        return;
      }
      this.dynamicOptions = { ...this.dynamicOptions, [name]: options };
    } catch (error) {
      if (this.disposed) {
        return;
      }
      this.dynamicErrors = {
        ...this.dynamicErrors,
        [name]: error instanceof ApiError ? error.message : 'Could not load options.',
      };
    } finally {
      if (!this.disposed) {
        this.dynamicLoading = { ...this.dynamicLoading, [name]: false };
      }
    }
  }

  /** Open the shared browser modal configured for the given PATH/TAG parameter entry. */
  private openBrowser(parameter: ReportParameter, index: number | null): void {
    if (!this.pathBrowser) {
      return;
    }
    const isTag = parameter.type === 'TAG';
    this.browsing = { name: parameter.name, index, isTag };
    this.pathBrowser.browseType = isTag ? 'TAG' : ((parameter.pathType as BrowseType) || 'NODE');
    this.pathBrowser.rootPath = parameter.rootPath ?? (isTag ? '/content/cq:tags' : '');
    void this.pathBrowser.openBrowser(isTag ? '' : this.valueAt(parameter.name, index));
  }

  private valueAt(name: string, index: number | null): string {
    return index === null ? this.valueScalar(name) : (this.valueList(name)[index] ?? '');
  }

  private onPathSelected(event: CustomEvent<{ path: string; id?: string | null }>): void {
    if (!this.browsing) {
      return;
    }
    const { name, index, isTag } = this.browsing;
    const selected = isTag ? (event.detail.id ?? event.detail.path) : event.detail.path;
    this.setValue(name, index, selected);
    this.browsing = null;
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

