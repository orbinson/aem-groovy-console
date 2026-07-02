import { html, LitElement, nothing } from 'lit';
import { customElement, property, query, state } from 'lit/decorators.js';
import { ApiError } from '@console/api/client';
import { deleteReport, getReport, previewReport, saveReport } from '../../api/reports-api';
import type {
  PathType,
  ReportParameter,
  ReportParameterType,
  ReportPreviewResponse,
  SaveReportRequest,
} from '../../api/reports-types';
import { toast } from './gcr-app';
import type { GcrCodeEditor } from './gcr-code-editor';
import { mutePlaceholders } from '@console/util/mute-placeholders';
import { renderResultTable } from './result-cell';
import { validateRequired } from './validate-parameters';

const PARAMETER_TYPES: ReportParameterType[] = ['STRING', 'NUMBER', 'BOOLEAN', 'DATE', 'SELECT', 'PATH'];

const DEFAULT_SCRIPT = [
  'import be.orbinson.aem.groovy.console.reports.data.ReportColumnType',
  '',
  'def data = report.data()',
  '',
  "data.column('Name')",
  "data.column('Path', ReportColumnType.LINK)",
  '// UI-only column: shown in the table but omitted from the CSV/XLSX export (exported = false)',
  "data.column('Edit', ReportColumnType.LINK, false)",
  '',
  "// def resource = resourceResolver.getResource(params.path)",
  '',
  'data',
  '',
].join('\n');

interface ParameterRow extends Omit<ReportParameter, 'options'> {
  /** Comma-separated in the editor form. */
  options: string;
}

/** Editor view: metadata, Groovy script (Monaco) and parameters. Access is governed by JCR ACLs. */
@customElement('gcr-report-editor')
export class GcrReportEditor extends LitElement {
  /** Report name, or null when creating a new report. */
  @property() name: string | null = null;

  @state() private loaded = false;
  @state() private error: string | null = null;
  @state() private saving = false;
  @state() private isNew = true;

  @state() private reportName = '';
  @state() private reportTitle = '';
  @state() private description = '';
  @state() private category = '';
  @state() private pageSize = '';
  @state() private script = DEFAULT_SCRIPT;
  @state() private parameters: ParameterRow[] = [];

  // "try out" run state
  @state() private testValues: Record<string, string> = {};
  @state() private preview: ReportPreviewResponse | null = null;
  @state() private previewing = false;
  /** Validation messages for required test values left empty, keyed by parameter name. */
  @state() private testErrors: Record<string, string> = {};

  @query('gcr-code-editor') private codeEditor?: GcrCodeEditor;

  createRenderRoot(): this {
    return this;
  }

  protected updated(): void {
    mutePlaceholders(this);
  }

  connectedCallback(): void {
    super.connectedCallback();
    // Lazy-load the Monaco-based editor component (and Monaco itself) only when the editor view
    // is actually opened; the list and run views stay lightweight.
    void import('./gcr-code-editor');
  }

  protected willUpdate(changed: Map<string, unknown>): void {
    if (changed.has('name')) {
      void this.load();
    }
  }

  private async load(): Promise<void> {
    this.loaded = false;
    this.error = null;
    this.isNew = !this.name;

    if (!this.name) {
      this.loaded = true;
      return;
    }

    try {
      const definition = await getReport(this.name);

      if (!definition.canEdit) {
        this.error = 'You are not allowed to edit this report.';
        return;
      }

      this.reportName = definition.name;
      this.reportTitle = definition.title ?? '';
      this.description = definition.description ?? '';
      this.category = definition.category ?? '';
      this.pageSize = definition.pageSize ? String(definition.pageSize) : '';
      this.script = definition.script ?? '';
      this.parameters = definition.parameters.map((parameter) => ({
        ...parameter,
        options: parameter.options.join(', '),
      }));
    } catch (error) {
      this.error = error instanceof ApiError ? error.message : 'Could not load report.';
    } finally {
      this.loaded = true;
    }
  }

  private addParameter(): void {
    this.parameters = [
      ...this.parameters,
      {
        name: '',
        label: '',
        type: 'STRING',
        defaultValue: '',
        required: false,
        options: '',
        pathType: 'NODE',
        rootPath: '',
        order: this.parameters.length,
      },
    ];
  }

  private updateParameter(index: number, patch: Partial<ParameterRow>): void {
    this.parameters = this.parameters.map((parameter, i) => (i === index ? { ...parameter, ...patch } : parameter));
  }

  private removeParameter(index: number): void {
    this.parameters = this.parameters.filter((_, i) => i !== index);
  }

  private async save(): Promise<void> {
    if (this.saving) {
      return;
    }

    const name = this.reportName.trim();

    if (!name) {
      toast(this, 'A report name is required.', 'negative');
      return;
    }

    this.saving = true;

    try {
      const definition = await saveReport(this.buildDefinition(name));

      toast(this, 'Report saved.');
      window.location.hash = `#/report/${encodeURIComponent(definition.name)}`;
    } catch (error) {
      toast(this, error instanceof ApiError ? error.message : 'Could not save report.', 'negative');
    } finally {
      this.saving = false;
    }
  }

  /** Build the definition payload from the current editor state (shared by save and the try-out preview). */
  private buildDefinition(name = this.reportName.trim()): SaveReportRequest {
    return {
      name,
      title: this.reportTitle.trim() || name,
      description: this.description.trim() || undefined,
      category: this.category.trim() || undefined,
      script: this.codeEditor?.value ?? this.script,
      pageSize: this.pageSize ? Number(this.pageSize) : undefined,
      parameters: this.parameters.map((parameter, index) => ({
        name: parameter.name.trim(),
        label: parameter.label.trim() || parameter.name.trim(),
        type: parameter.type,
        defaultValue: parameter.defaultValue || undefined,
        required: parameter.required,
        options: parameter.options
          .split(',')
          .map((option) => option.trim())
          .filter(Boolean),
        pathType: parameter.type === 'PATH' ? parameter.pathType || 'NODE' : undefined,
        rootPath: parameter.type === 'PATH' ? parameter.rootPath || undefined : undefined,
        order: index,
      })),
    };
  }

  /** Run the current (unsaved) script with the test values and show the typed result inline. */
  private async runPreview(): Promise<void> {
    if (this.previewing) {
      return;
    }

    const errors = validateRequired(this.parameters, this.testValues);
    this.testErrors = errors;
    if (Object.keys(errors).length) {
      return;
    }

    this.previewing = true;
    this.preview = null;

    try {
      this.preview = await previewReport(this.buildDefinition(this.reportName.trim() || 'preview'), this.testValues);
    } catch (error) {
      toast(this, error instanceof ApiError ? error.message : 'Could not run the report.', 'negative');
    } finally {
      this.previewing = false;
    }
  }

  private async removeReport(): Promise<void> {
    if (!this.name || !window.confirm(`Delete report "${this.reportTitle || this.name}"? This cannot be undone.`)) {
      return;
    }

    try {
      await deleteReport(this.name);
      toast(this, 'Report deleted.');
      window.location.hash = '#/';
    } catch (error) {
      toast(this, error instanceof ApiError ? error.message : 'Could not delete report.', 'negative');
    }
  }

  protected render() {
    if (!this.loaded) {
      return html`<div class="gcr-loading" role="status">
        <sp-progress-circle indeterminate size="l"></sp-progress-circle>
        <span class="gcr-visually-hidden">Loading…</span>
      </div>`;
    }

    if (this.error) {
      return html`<div class="gcr-empty" role="alert">${this.error}</div>`;
    }

    return html`
      <div class="gcr-page">
        <div class="gcr-breadcrumbs">
          <a href="#/">Reports</a> /
          ${this.isNew
            ? 'New report'
            : html`<a href="#/report/${encodeURIComponent(this.name!)}">${this.reportTitle || this.name}</a> / Edit`}
        </div>

        <div class="gcr-page-header">
          <h1>${this.isNew ? 'New report' : `Edit: ${this.reportTitle || this.name}`}</h1>
          <div class="gcr-page-header-actions">
            ${!this.isNew
              ? html`
                  <sp-button variant="negative" treatment="outline" @click=${() => void this.removeReport()}>
                    Delete
                  </sp-button>
                `
              : nothing}
            <sp-button ?disabled=${this.saving} @click=${() => void this.save()}>
              ${this.saving ? 'Saving…' : 'Save'}
            </sp-button>
          </div>
        </div>

        <section class="gcr-panel">
          <h3>Details</h3>
          <div class="gcr-form-grid">
            <div class="gcr-field">
              <sp-field-label for="report-name" required>Name</sp-field-label>
              <sp-textfield
                id="report-name"
                value=${this.reportName}
                ?disabled=${!this.isNew}
                placeholder="my-report"
                @input=${(event: Event) => (this.reportName = (event.target as HTMLInputElement).value)}
              ></sp-textfield>
              ${this.isNew
                ? html`<sp-help-text size="s">Letters, digits, '-' and '_' only; cannot be changed later.</sp-help-text>`
                : nothing}
            </div>
            <div class="gcr-field">
              <sp-field-label for="report-title">Title</sp-field-label>
              <sp-textfield
                id="report-title"
                value=${this.reportTitle}
                @input=${(event: Event) => (this.reportTitle = (event.target as HTMLInputElement).value)}
              ></sp-textfield>
            </div>
            <div class="gcr-field">
              <sp-field-label for="report-category">Category</sp-field-label>
              <sp-textfield
                id="report-category"
                value=${this.category}
                @input=${(event: Event) => (this.category = (event.target as HTMLInputElement).value)}
              ></sp-textfield>
            </div>
            <div class="gcr-field">
              <sp-field-label for="report-page-size">Page size</sp-field-label>
              <sp-textfield
                id="report-page-size"
                type="number"
                value=${this.pageSize}
                placeholder="50"
                @input=${(event: Event) => (this.pageSize = (event.target as HTMLInputElement).value)}
              ></sp-textfield>
            </div>
            <div class="gcr-field gcr-field-wide">
              <sp-field-label for="report-description">Description</sp-field-label>
              <sp-textfield
                id="report-description"
                multiline
                value=${this.description}
                @input=${(event: Event) => (this.description = (event.target as HTMLInputElement).value)}
              ></sp-textfield>
            </div>
          </div>
        </section>

        <section class="gcr-panel">
          <h3>Script</h3>
          <div class="gcr-editor-frame">
            <gcr-code-editor .initialValue=${this.script}></gcr-code-editor>
          </div>
          <sp-help-text size="s">
            The script receives the <code>params</code> and <code>report</code> bindings and should return
            <code>report.data()</code> (typed columns) or a console <code>Table</code>. Who may view, run, edit
            and create reports is controlled by repository permissions on <code>/conf/groovyconsole/reports</code>.
          </sp-help-text>
        </section>

        ${this.renderTryOut()}

        <section class="gcr-panel">
          <div class="gcr-panel-header">
            <h3>Parameters</h3>
            <sp-action-button size="s" @click=${() => this.addParameter()}>Add parameter</sp-action-button>
          </div>
          ${this.parameters.length === 0
            ? html`<div class="gcr-empty">No parameters. The report runs without input.</div>`
            : this.parameters.map((parameter, index) => this.renderParameterRow(parameter, index))}
        </section>
      </div>
    `;
  }

  private renderTryOut() {
    return html`
      <section class="gcr-panel">
        <div class="gcr-panel-header">
          <h3>Try it out</h3>
          <sp-button size="s" ?disabled=${this.previewing} @click=${() => void this.runPreview()}>
            ${this.previewing ? 'Running…' : 'Run ▶'}
          </sp-button>
        </div>

        ${this.parameters.length
          ? html`<div class="gcr-form-grid">${this.parameters.map((parameter) => this.renderTestValue(parameter))}</div>`
          : html`<sp-help-text size="s">Runs the current script with no parameters. Nothing is saved.</sp-help-text>`}

        ${this.previewing
          ? html`<div class="gcr-loading" role="status">
              <sp-progress-circle indeterminate size="m"></sp-progress-circle>
              <span class="gcr-visually-hidden">Running…</span>
            </div>`
          : nothing}
        ${this.preview ? this.renderPreviewResult(this.preview) : nothing}
      </section>
    `;
  }

  private renderTestValue(parameter: ParameterRow) {
    const label = parameter.label || parameter.name || '(unnamed)';
    const error = this.testErrors[parameter.name];
    const current = this.testValues[parameter.name] ?? parameter.defaultValue ?? '';
    const onInput = (event: Event) => {
      this.testValues = { ...this.testValues, [parameter.name]: (event.target as HTMLInputElement).value };
      if (this.testErrors[parameter.name]) {
        const { [parameter.name]: _cleared, ...rest } = this.testErrors;
        this.testErrors = rest;
      }
    };
    // mirror the run form's typed inputs so the try-out behaves like a real run (e.g. a date picker for DATE)
    const field =
      parameter.type === 'DATE'
        ? html`<input
            id="test-${parameter.name}"
            class="gcr-date-input"
            type="date"
            aria-label="Test value for ${label}"
            .value=${current}
            @input=${onInput}
          />`
        : html`<sp-textfield
            id="test-${parameter.name}"
            aria-label="Test value for ${label}"
            type=${parameter.type === 'NUMBER' ? 'number' : 'text'}
            ?invalid=${!!error}
            value=${current}
            @input=${onInput}
          ></sp-textfield>`;
    return html`
      <div class="gcr-field">
        <sp-field-label for="test-${parameter.name}" ?required=${parameter.required}>${label}</sp-field-label>
        ${field}
        ${error ? html`<sp-help-text variant="negative" role="alert">${error}</sp-help-text>` : nothing}
      </div>
    `;
  }

  private renderPreviewResult(preview: ReportPreviewResponse) {
    if (preview.status === 'FAILED') {
      return html`
        <div class="gcr-error-panel" role="alert">
          <h4>Run failed</h4>
          <pre class="gcr-stacktrace">${preview.exceptionStackTrace}</pre>
        </div>
      `;
    }

    return html`
      <div class="gcr-result-summary" role="status" aria-live="polite">
        ${preview.rowCount} rows · ${preview.runningTime ?? ''}
      </div>
      ${preview.output
        ? html`<details class="gcr-output-details">
            <summary>Script output</summary>
            <pre class="gcr-output">${preview.output}</pre>
          </details>`
        : nothing}
      ${preview.columns.length
        ? renderResultTable(preview.columns, preview.rows)
        : html`<div class="gcr-empty">The report returned no rows.</div>`}
    `;
  }

  private renderParameterRow(parameter: ParameterRow, index: number) {
    return html`
      <div class="gcr-parameter-row" role="group" aria-label="Parameter ${index + 1}">
        <sp-textfield
          class="gcr-parameter-name"
          aria-label="Parameter name"
          value=${parameter.name}
          placeholder="name"
          @input=${(event: Event) => this.updateParameter(index, { name: (event.target as HTMLInputElement).value })}
        ></sp-textfield>
        <sp-textfield
          class="gcr-parameter-label"
          aria-label="Parameter label"
          value=${parameter.label}
          placeholder="Label"
          @input=${(event: Event) => this.updateParameter(index, { label: (event.target as HTMLInputElement).value })}
        ></sp-textfield>
        <sp-picker
          class="gcr-parameter-type"
          aria-label="Parameter type"
          value=${parameter.type}
          @change=${(event: Event) =>
            this.updateParameter(index, { type: (event.target as HTMLInputElement).value as ReportParameterType })}
        >
          ${PARAMETER_TYPES.map((type) => html`<sp-menu-item value=${type}>${type}</sp-menu-item>`)}
        </sp-picker>
        <sp-textfield
          class="gcr-parameter-default"
          aria-label="Default value"
          value=${parameter.defaultValue ?? ''}
          placeholder="Default"
          @input=${(event: Event) =>
            this.updateParameter(index, { defaultValue: (event.target as HTMLInputElement).value })}
        ></sp-textfield>
        ${parameter.type === 'SELECT'
          ? html`
              <sp-textfield
                class="gcr-parameter-options"
                aria-label="Select options (comma separated)"
                value=${parameter.options}
                placeholder="option1, option2"
                @input=${(event: Event) =>
                  this.updateParameter(index, { options: (event.target as HTMLInputElement).value })}
              ></sp-textfield>
            `
          : nothing}
        ${parameter.type === 'PATH'
          ? html`
              <sp-picker
                class="gcr-parameter-pathtype"
                label="Browser"
                aria-label="Path browser type"
                value=${parameter.pathType || 'NODE'}
                @change=${(event: Event) =>
                  this.updateParameter(index, { pathType: (event.target as HTMLInputElement).value as PathType })}
              >
                <sp-menu-item value="NODE">Any node</sp-menu-item>
                <sp-menu-item value="PAGE">Pages</sp-menu-item>
                <sp-menu-item value="ASSET">Assets</sp-menu-item>
              </sp-picker>
              <sp-textfield
                class="gcr-parameter-rootpath"
                aria-label="Path browser root"
                value=${parameter.rootPath ?? ''}
                placeholder="Root path (optional)"
                @input=${(event: Event) =>
                  this.updateParameter(index, { rootPath: (event.target as HTMLInputElement).value })}
              ></sp-textfield>
            `
          : nothing}
        <sp-checkbox
          ?checked=${parameter.required}
          @change=${(event: Event) =>
            this.updateParameter(index, { required: (event.target as HTMLInputElement).checked })}
        >
          Required
        </sp-checkbox>
        <sp-action-button size="s" quiet @click=${() => this.removeParameter(index)} aria-label="Remove parameter">
          ✕
        </sp-action-button>
      </div>
    `;
  }
}
