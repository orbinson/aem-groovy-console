import { html, LitElement, nothing } from 'lit';
import { customElement, property, query, state } from 'lit/decorators.js';
import { ApiError } from '@console/api/client';
import {
  deleteReport,
  getReport,
  previewReport,
  resolveDynamicOptionsForScript,
  saveReport,
} from '../../api/reports-api';
import type {
  BrowseType,
  PathType,
  ReportParameter,
  ReportParameterType,
  ReportParameterValue,
  ReportPreviewResponse,
  SaveReportRequest,
} from '../../api/reports-types';
import { toast } from './gcr-app';
import type { GcrCodeEditor } from './gcr-code-editor';
import type { GcrPathBrowser } from './gcr-path-browser';
import { mutePlaceholders } from '@console/util/mute-placeholders';
import { renderResultTable } from './result-cell';
import { validateRequired } from './validate-parameters';

const PARAMETER_TYPES: ReportParameterType[] = [
  'STRING',
  'NUMBER',
  'BOOLEAN',
  'DATE',
  'SELECT',
  'PATH',
  'TAG',
  'DYNAMIC',
];

const DEFAULT_OPTIONS_SCRIPT = [
  'def options = report.options()',
  '',
  '// Return value/label pairs — the value is submitted, the label is shown.',
  '// This lists the child resources of /content (works on AEM and Sling); replace with your own logic.',
  "resourceResolver.getResource('/content')?.listChildren()?.each { child ->",
  '    options.add(child.path, child.name)',
  '}',
  '',
  'options',
  '',
].join('\n');

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
  /** Whether the user may edit the executable Groovy; metadata/parameters stay editable regardless. */
  @state() private canEditScript = true;

  // "try out" run state
  @state() private testValues: Record<string, string | string[]> = {};
  @state() private preview: ReportPreviewResponse | null = null;
  @state() private previewing = false;
  /** Validation messages for required test values left empty, keyed by parameter name. */
  @state() private testErrors: Record<string, string> = {};

  @query('gcr-code-editor') private codeEditor?: GcrCodeEditor;
  @query('gcr-path-browser') private rootBrowser?: GcrPathBrowser;

  /** Index of the parameter whose root path is being picked in the browser. */
  private browsingRootIndex: number | null = null;

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
      this.canEditScript = definition.canEditScript ?? false;
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
        multiple: false,
        options: '',
        pathType: 'NODE',
        rootPath: '',
        optionsScript: '',
        order: this.parameters.length,
      },
    ];
  }

  private updateParameter(index: number, patch: Partial<ParameterRow>): void {
    this.parameters = this.parameters.map((parameter, i) => (i === index ? { ...parameter, ...patch } : parameter));
  }

  /** Change a parameter's type, seeding a starter options script the first time it becomes DYNAMIC. */
  private changeParameterType(index: number, type: ReportParameterType): void {
    const patch: Partial<ParameterRow> = { type };
    if (type === 'DYNAMIC' && !this.parameters[index].optionsScript) {
      patch.optionsScript = DEFAULT_OPTIONS_SCRIPT;
    }
    this.updateParameter(index, patch);
  }

  /** Open the repository/taxonomy browser to pick a PATH or TAG parameter's root. */
  private openRootBrowser(index: number, parameter: ParameterRow): void {
    if (!this.rootBrowser) {
      return;
    }
    const isTag = parameter.type === 'TAG';
    this.browsingRootIndex = index;
    this.rootBrowser.browseType = isTag ? 'TAG' : ((parameter.pathType as BrowseType) || 'NODE');
    this.rootBrowser.rootPath = isTag ? '/content/cq:tags' : '';
    void this.rootBrowser.openBrowser(parameter.rootPath ?? '');
  }

  private onRootSelected(event: CustomEvent<{ path: string; id?: string | null }>): void {
    if (this.browsingRootIndex === null) {
      return;
    }
    // the root is always a JCR path (the taxonomy/tag node path for TAG, a node path for PATH)
    this.updateParameter(this.browsingRootIndex, { rootPath: event.detail.path });
    this.browsingRootIndex = null;
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
        multiple: parameter.multiple,
        options: parameter.options
          .split(',')
          .map((option) => option.trim())
          .filter(Boolean),
        pathType: parameter.type === 'PATH' ? parameter.pathType || 'NODE' : undefined,
        // rootPath scopes the PATH browser and the TAG taxonomy root
        rootPath:
          parameter.type === 'PATH' || parameter.type === 'TAG' ? parameter.rootPath || undefined : undefined,
        optionsScript: parameter.type === 'DYNAMIC' ? parameter.optionsScript || undefined : undefined,
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
      this.preview = await previewReport(this.buildDefinition(this.reportName.trim() || 'preview'), this.testValuesForRun());
    } catch (error) {
      toast(this, error instanceof ApiError ? error.message : 'Could not run the report.', 'negative');
    } finally {
      this.previewing = false;
    }
  }

  /** Test values for the try-out run: a `multiple` parameter's newline-separated text becomes a string[]. */
  private testValuesForRun(): Record<string, ReportParameterValue> {
    const values: Record<string, ReportParameterValue> = {};
    for (const parameter of this.parameters) {
      if (parameter.multiple) {
        values[parameter.name] = this.testValueList(parameter.name)
          .map((entry) => entry.trim())
          .filter(Boolean);
      } else {
        const raw = this.testValues[parameter.name];
        values[parameter.name] = (Array.isArray(raw) ? raw[0] : raw) ?? parameter.defaultValue ?? '';
      }
    }
    return values;
  }

  // test-value helpers (a `multiple` parameter's test value is an array, like the run form)

  private testValueList(name: string): string[] {
    const value = this.testValues[name];
    if (Array.isArray(value)) {
      return value.length ? value : [''];
    }
    return value ? [value] : [''];
  }

  private clearTestError(name: string): void {
    if (this.testErrors[name]) {
      const { [name]: _cleared, ...rest } = this.testErrors;
      this.testErrors = rest;
    }
  }

  private setTestValue(name: string, index: number | null, value: string): void {
    if (index === null) {
      this.testValues = { ...this.testValues, [name]: value };
    } else {
      const next = [...this.testValueList(name)];
      next[index] = value;
      this.testValues = { ...this.testValues, [name]: next };
    }
    this.clearTestError(name);
  }

  private addTestValue(name: string): void {
    this.testValues = { ...this.testValues, [name]: [...this.testValueList(name), ''] };
  }

  private removeTestValue(name: string, index: number): void {
    const next = this.testValueList(name).filter((_, i) => i !== index);
    this.testValues = { ...this.testValues, [name]: next.length ? next : [''] };
    this.clearTestError(name);
  }

  /** Resolve options for an unsaved DYNAMIC parameter and report the count (used by the editor's Test button). */
  private async testOptions(parameter: ParameterRow): Promise<void> {
    try {
      const options = await resolveDynamicOptionsForScript(parameter.optionsScript ?? '', this.testValuesForRun());
      toast(this, `Options script returned ${options.length} option(s).`);
    } catch (error) {
      toast(this, error instanceof ApiError ? error.message : 'Options script failed.', 'negative');
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
      <div
        class="gcr-page"
        @gcr-path-selected=${(event: CustomEvent<{ path: string; id?: string | null }>) =>
          this.onRootSelected(event)}
      >
        <gcr-path-browser></gcr-path-browser>
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
            <gcr-code-editor .initialValue=${this.script} ?readOnly=${!this.canEditScript}></gcr-code-editor>
          </div>
          <sp-help-text size="s">
            The script receives the <code>params</code> and <code>report</code> bindings and should return
            <code>report.data()</code> (typed columns) or a console <code>Table</code>. Who may view, run, edit
            and create reports is controlled by repository permissions on <code>/conf/groovyconsole/reports</code>.
          </sp-help-text>
          ${!this.canEditScript
            ? html`<sp-help-text size="s" variant="neutral">
                The script is read-only — editing it requires console permission. You can still update the report
                details and parameters.
              </sp-help-text>`
            : nothing}
        </section>

        ${this.canEditScript ? this.renderTryOut() : nothing}

        <section class="gcr-panel">
          <div class="gcr-panel-header">
            <h3>Parameters</h3>
            ${this.canEditScript
              ? html`<sp-action-button size="s" @click=${() => this.addParameter()}>Add parameter</sp-action-button>`
              : nothing}
          </div>
          ${this.parameters.length === 0
            ? html`<div class="gcr-empty">No parameters. The report runs without input.</div>`
            : this.canEditScript
              ? this.parameters.map((parameter, index) => this.renderParameterRow(parameter, index))
              : this.parameters.map((parameter) => this.renderParameterSummary(parameter))}
          ${!this.canEditScript
            ? html`<sp-help-text size="s" variant="neutral">
                Parameters can only be changed with console permission. You can still update the report details.
              </sp-help-text>`
            : nothing}
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
            <sp-icon-play slot="icon"></sp-icon-play>
            ${this.previewing ? 'Running…' : 'Run'}
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

    // mirror the run form: a `multiple` parameter uses a multifield (rows with add/remove), not one value per line
    const field = parameter.multiple
      ? html`
          <div class="gcr-multifield">
            ${this.testValueList(parameter.name).map(
              (entry, index) => html`
                <div class="gcr-multifield-row">
                  ${this.renderTestInput(parameter, `test-${parameter.name}-${index}`, entry, !!error, index)}
                  <sp-action-button
                    quiet
                    size="s"
                    class="gcr-multifield-remove"
                    label="Remove value"
                    title="Remove value"
                    @click=${() => this.removeTestValue(parameter.name, index)}
                  >
                    <sp-icon-close slot="icon"></sp-icon-close>
                  </sp-action-button>
                </div>
              `,
            )}
          </div>
          <sp-action-button quiet size="s" class="gcr-multifield-add" @click=${() => this.addTestValue(parameter.name)}>
            <sp-icon-add slot="icon"></sp-icon-add>
            Add value
          </sp-action-button>
        `
      : this.renderTestInput(
          parameter,
          `test-${parameter.name}`,
          (this.testValues[parameter.name] as string) ?? parameter.defaultValue ?? '',
          !!error,
          null,
        );

    return html`
      <div class="gcr-field">
        <sp-field-label for="test-${parameter.name}" ?required=${parameter.required}>${label}</sp-field-label>
        ${field}
        ${error ? html`<sp-help-text variant="negative" role="alert">${error}</sp-help-text>` : nothing}
      </div>
    `;
  }

  /** A single test-value input (DATE picker or text/number field), writing to the test values at `index`. */
  private renderTestInput(parameter: ParameterRow, id: string, value: string, invalid: boolean, index: number | null) {
    const onInput = (event: Event) =>
      this.setTestValue(parameter.name, index, (event.target as HTMLInputElement).value);

    if (parameter.type === 'DATE') {
      return html`<input id=${id} class="gcr-date-input" type="date" .value=${value} @input=${onInput} />`;
    }
    return html`<sp-textfield
      id=${id}
      aria-label="Test value for ${parameter.label || parameter.name}"
      type=${parameter.type === 'NUMBER' ? 'number' : 'text'}
      ?invalid=${invalid}
      value=${value}
      @input=${onInput}
    ></sp-textfield>`;
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

  /** Read-only one-line view of a parameter, shown when the user may not edit the definition (no console rights). */
  private renderParameterSummary(parameter: ParameterRow) {
    const flags = [parameter.type, parameter.multiple ? 'multiple' : '', parameter.required ? 'required' : '']
      .filter(Boolean)
      .join(' · ');
    return html`
      <div class="gcr-parameter-summary">
        <strong>${parameter.label || parameter.name || '(unnamed)'}</strong>
        <span class="gcr-browser-subtle">${parameter.name} — ${flags}</span>
      </div>
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
          @change=${(event: Event) => this.changeParameterType(index, (event.target as HTMLInputElement).value as ReportParameterType)}
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
              ${this.renderRootField(parameter, index, 'Root path (optional)')}
            `
          : nothing}
        ${parameter.type === 'TAG'
          ? this.renderRootField(parameter, index, 'Taxonomy root (optional)')
          : nothing}
        <div class="gcr-parameter-actions">
          <sp-checkbox
            ?checked=${parameter.multiple}
            @change=${(event: Event) =>
              this.updateParameter(index, { multiple: (event.target as HTMLInputElement).checked })}
          >
            Multiple
          </sp-checkbox>
          <sp-checkbox
            ?checked=${parameter.required}
            @change=${(event: Event) =>
              this.updateParameter(index, { required: (event.target as HTMLInputElement).checked })}
          >
            Required
          </sp-checkbox>
          <sp-action-button size="s" quiet @click=${() => this.removeParameter(index)} aria-label="Remove parameter">
            <sp-icon-close slot="icon"></sp-icon-close>
          </sp-action-button>
        </div>
      </div>
      ${parameter.type === 'DYNAMIC' ? this.renderOptionsScript(parameter, index) : nothing}
    `;
  }

  /** The root/taxonomy field for PATH and TAG parameters: a text field plus a browse button. */
  private renderRootField(parameter: ParameterRow, index: number, placeholder: string) {
    return html`
      <div class="gcr-parameter-rootpath gcr-path-field">
        <sp-textfield
          aria-label=${placeholder}
          value=${parameter.rootPath ?? ''}
          placeholder=${placeholder}
          @input=${(event: Event) =>
            this.updateParameter(index, { rootPath: (event.target as HTMLInputElement).value })}
        ></sp-textfield>
        <sp-action-button size="s" quiet title="Browse" aria-label="Browse for root"
          @click=${() => this.openRootBrowser(index, parameter)}>
          <sp-icon-folder-open slot="icon"></sp-icon-folder-open>
        </sp-action-button>
      </div>
    `;
  }

  /** The Groovy options-script editor shown for a DYNAMIC parameter. */
  private renderOptionsScript(parameter: ParameterRow, index: number) {
    return html`
      <div class="gcr-options-script-field" role="group" aria-label="Options script for parameter ${index + 1}">
        <sp-field-label for="options-script-${index}">
          Options script for <strong>${parameter.label || parameter.name || 'this parameter'}</strong> — return
          <code>report.options()</code> of value/label pairs
        </sp-field-label>
        <textarea
          id="options-script-${index}"
          class="gcr-options-script"
          spellcheck="false"
          autocomplete="off"
          autocapitalize="off"
          aria-label="Dynamic options Groovy script"
          ?disabled=${!this.canEditScript}
          .value=${parameter.optionsScript || DEFAULT_OPTIONS_SCRIPT}
          @input=${(event: Event) =>
            this.updateParameter(index, { optionsScript: (event.target as HTMLTextAreaElement).value })}
        ></textarea>
        ${this.canEditScript
          ? html`<div class="gcr-options-script-actions">
              <sp-button variant="secondary" size="s" @click=${() => void this.testOptions(parameter)}>
                <sp-icon-play slot="icon"></sp-icon-play>
                Test options
              </sp-button>
            </div>`
          : nothing}
      </div>
    `;
  }
}
