import { html, LitElement, nothing } from 'lit';
import { customElement, property, query, state } from 'lit/decorators.js';
import { ApiError } from '@console/api/client';
import { listChildPaths, resolveDynamicOptions } from '../../api/reports-api';
import type { BrowseType, DynamicOption, ReportParameter, ReportParameterValue } from '../../api/reports-types';
import { renderMultifield } from './multifield';
import type { GcrPathBrowser } from './gcr-path-browser';

/**
 * Reusable typed inputs for a set of report parameters, bound to a values map. Renders the same rich controls as
 * the run form — dropdowns (SELECT/DYNAMIC), a repository/tag browser (PATH/TAG), a date picker (DATE), a checkbox
 * (BOOLEAN) and a multifield for `multiple` parameters — and emits a `gcr-values-change` event carrying the full
 * updated values map. The host owns the values; this component owns the auxiliary UI state (dynamic options,
 * path suggestions, the browser dialog).
 */
@customElement('gcr-parameter-fields')
export class GcrParameterFields extends LitElement {
  @property({ attribute: false }) parameters: ReportParameter[] = [];
  @property({ attribute: false }) values: Record<string, ReportParameterValue> = {};
  /** Report name, needed to resolve a saved DYNAMIC parameter's options. */
  @property() reportName = '';
  /** Validation messages to surface per parameter, keyed by name. */
  @property({ attribute: false }) errors: Record<string, string> = {};

  @state() private pathSuggestions: Record<string, string[]> = {};
  @state() private dynamicOptions: Record<string, DynamicOption[]> = {};
  @state() private dynamicLoading: Record<string, boolean> = {};
  @state() private dynamicErrors: Record<string, string> = {};

  private browsing: { name: string; index: number | null; isTag: boolean } | null = null;
  private pathSuggestionSeq: Record<string, number> = {};
  private disposed = false;

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

  render() {
    return html`
      <div
        @gcr-path-selected=${(event: CustomEvent<{ path: string; id?: string | null }>) =>
          this.onPathSelected(event)}
      >
        <gcr-path-browser></gcr-path-browser>
        <div class="gcr-form-grid">${this.parameters.map((parameter) => this.renderParameter(parameter))}</div>
      </div>
    `;
  }

  private renderParameter(parameter: ReportParameter) {
    const error = this.errors[parameter.name];
    const label = html`
      <sp-field-label for="pv-${parameter.name}" ?required=${parameter.required}>
        ${parameter.label || parameter.name}
      </sp-field-label>
    `;
    const errorMessage = error
      ? html`<sp-help-text variant="negative" role="alert">${error}</sp-help-text>`
      : nothing;

    if (parameter.multiple) {
      return html`
        <div class="gcr-field">
          ${label}
          ${renderMultifield({
            entries: this.valueList(parameter.name),
            renderEntry: (entry, index) =>
              this.renderControl(
                parameter,
                index === 0 ? `pv-${parameter.name}` : `pv-${parameter.name}-${index}`,
                entry,
                !!error,
                index,
              ),
            onAdd: () => this.addValue(parameter.name),
            onRemove: (index) => this.removeValue(parameter.name, index),
          })}
          ${errorMessage}
        </div>
      `;
    }

    if (parameter.type === 'BOOLEAN') {
      return html`
        <div class="gcr-field">
          ${this.renderControl(parameter, `pv-${parameter.name}`, this.valueScalar(parameter.name), !!error, null)}
        </div>
      `;
    }

    return html`
      <div class="gcr-field">
        ${label}
        ${this.renderControl(parameter, `pv-${parameter.name}`, this.valueScalar(parameter.name), !!error, null)}
        ${errorMessage}
      </div>
    `;
  }

  private renderControl(parameter: ReportParameter, id: string, value: string, invalid: boolean, index: number | null) {
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
          list="pv-paths-${id}"
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
        <datalist id="pv-paths-${id}">
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

  /** Apply a change to the values map and notify the host. */
  private emitValues(values: Record<string, ReportParameterValue>): void {
    this.values = values;
    this.dispatchEvent(new CustomEvent('gcr-values-change', { detail: values, bubbles: true, composed: true }));
  }

  private setValue(name: string, index: number | null, newValue: string): void {
    if (index === null) {
      this.emitValues({ ...this.values, [name]: newValue });
    } else {
      const next = [...this.valueList(name)];
      next[index] = newValue;
      this.emitValues({ ...this.values, [name]: next });
    }
  }

  private addValue(name: string): void {
    this.emitValues({ ...this.values, [name]: [...this.valueList(name), ''] });
  }

  private removeValue(name: string, index: number): void {
    const next = this.valueList(name).filter((_, entryIndex) => entryIndex !== index);
    this.emitValues({ ...this.values, [name]: next.length ? next : [''] });
  }

  private async loadDynamicOptions(parameter: ReportParameter): Promise<void> {
    const name = parameter.name;
    this.dynamicLoading = { ...this.dynamicLoading, [name]: true };
    this.dynamicErrors = { ...this.dynamicErrors, [name]: '' };

    try {
      const options = await resolveDynamicOptions(this.reportName, name, this.values);
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

  private async loadPathSuggestions(name: string, value: string): Promise<void> {
    const cut = value.lastIndexOf('/');
    const parent = cut > 0 ? value.slice(0, cut) : '/';

    const seq = (this.pathSuggestionSeq[name] ?? 0) + 1;
    this.pathSuggestionSeq[name] = seq;

    const children = await listChildPaths(parent);

    if (this.disposed || seq !== this.pathSuggestionSeq[name]) {
      return;
    }
    this.pathSuggestions = { ...this.pathSuggestions, [name]: children };
  }
}
