import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { unsafeHTML } from 'lit/directives/unsafe-html.js';
import type { RunScriptResponse, TableResult } from '../api/types';
import { getRunResultTabs, onExtensionsChanged } from '../extensions/registry';
import type { ConsoleRunResultTabExtension, ExtendedRunResult } from '../extensions/registry';
import { store, StoreController } from '../state/store';

/** Built-in tab ids, or the id of an extension-registered result tab. */
type DockTab = 'result' | 'log' | 'trace' | 'table' | string;

function parseTable(result?: string): TableResult | null {
  if (!result) {
    return null;
  }
  try {
    const table = (JSON.parse(result) as { table?: TableResult }).table;
    return table && Array.isArray(table.columns) && Array.isArray(table.rows) ? table : null;
  } catch {
    return null;
  }
}

/** Tabbed output dock: Result | Log | Trace | Table with running time badge and copy/download actions. */
@customElement('gc-result')
export class GcResult extends LitElement {
  private store = new StoreController(this);

  @state() private selectedTab: DockTab = 'log';

  private lastResult: RunScriptResponse | null = null;
  private unsubscribeExtensions?: () => void;

  createRenderRoot(): this {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    this.unsubscribeExtensions = onExtensionsChanged(() => this.requestUpdate());
  }

  disconnectedCallback(): void {
    this.unsubscribeExtensions?.();
    super.disconnectedCallback();
  }

  /** Extension result tabs that apply to the given run result (e.g. Query audit after an audited run). */
  private relevantExtensionTabs(result: RunScriptResponse | null): ConsoleRunResultTabExtension[] {
    if (!result) {
      return [];
    }
    return getRunResultTabs().filter((tab) => tab.isRelevant(result as ExtendedRunResult));
  }

  protected willUpdate(): void {
    const result = this.store.state.result;

    if (result !== this.lastResult) {
      this.lastResult = result;
      const extensionTabs = this.relevantExtensionTabs(result);
      // Default to the Log (output) tab — it's what's wanted most of the time. Errors still jump to the
      // trace; a relevant extension tab (present only for that extension's run mode) beats the log.
      if (result?.exceptionStackTrace?.length) {
        this.selectedTab = 'trace';
      } else if (extensionTabs.length) {
        this.selectedTab = extensionTabs[0].id;
      } else if (result?.output?.length) {
        this.selectedTab = 'log';
      } else if (parseTable(result?.result)) {
        this.selectedTab = 'table';
      } else if (result?.result?.length) {
        this.selectedTab = 'result';
      } else {
        this.selectedTab = 'log';
      }
    }
  }

  private copy(content: string): void {
    void navigator.clipboard.writeText(content).then(() => store.showToast('Copied to clipboard.'));
  }

  private download(content: string, fileName: string): void {
    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(url);
  }

  private activeContent(response: RunScriptResponse): { text: string; fileName: string } | null {
    switch (this.selectedTab) {
      case 'result':
        return response.result ? { text: response.result, fileName: 'result.txt' } : null;
      case 'log':
        return response.output ? { text: response.output, fileName: 'output.txt' } : null;
      case 'trace':
        return response.exceptionStackTrace
          ? { text: response.exceptionStackTrace, fileName: 'stacktrace.txt' }
          : null;
      default:
        return null;
    }
  }

  private gotoLine(line: number): void {
    this.dispatchEvent(new CustomEvent('gc-goto-line', { detail: { line }, bubbles: true, composed: true }));
  }

  /** Render a stacktrace with script frames (Script1.groovy:12) as clickable line links. */
  private renderTrace(trace: string) {
    const pattern = /(Script\d+\.groovy:(\d+))/g;
    const parts: Array<ReturnType<typeof html> | string> = [];
    let lastIndex = 0;
    let match: RegExpExecArray | null;

    while ((match = pattern.exec(trace)) !== null) {
      parts.push(trace.slice(lastIndex, match.index));

      const line = Number(match[2]);
      parts.push(
        html`<a class="gc-trace-link" role="button" title="Go to line ${line}"
              @click=${() => this.gotoLine(line)}>${match[1]}</a>`,
      );
      lastIndex = match.index + match[1].length;
    }
    parts.push(trace.slice(lastIndex));

    return html`<pre class="gc-dock-pre gc-dock-pre-error">${parts}</pre>`;
  }

  private renderTable(table: TableResult) {
    return html`
      <sp-table class="gc-dock-table">
        <sp-table-head>
          ${table.columns.map((column) => html`<sp-table-head-cell>${column}</sp-table-head-cell>`)}
        </sp-table-head>
        <sp-table-body>
          ${table.rows.map(
            (row) => html`
              <sp-table-row>
                ${(row as unknown[]).map((cell) => html`<sp-table-cell>${String(cell)}</sp-table-cell>`)}
              </sp-table-row>
            `,
          )}
        </sp-table-body>
      </sp-table>
    `;
  }

  protected updated(): void {
    // keep the live log scrolled to the bottom while streaming
    if (this.store.state.liveOutput !== null) {
      const body = this.querySelector('.gc-dock-body');
      body?.scrollTo({ top: body.scrollHeight });
    }

    // extension result tab: hand the run result to the extension's element (it renders itself)
    const extensionTab = getRunResultTabs().find((tab) => tab.id === this.selectedTab);
    if (extensionTab) {
      const element = this.querySelector(extensionTab.element) as (HTMLElement & { result?: unknown }) | null;
      if (element && element.result !== this.store.state.result) {
        element.result = this.store.state.result;
      }
    }
  }

  protected render() {
    const { result: response, liveOutput } = this.store.state;

    if (liveOutput !== null) {
      return html`
        <div class="gc-dock">
          <div class="gc-dock-header">
            <span class="gc-dock-title">Output</span>
            <sp-badge size="s" variant="informative">● live</sp-badge>
          </div>
          <div class="gc-dock-body">
            ${liveOutput.length
              ? html`<pre class="gc-dock-pre">${liveOutput}</pre>`
              : html`<div class="gc-dock-empty">Running…</div>`}
          </div>
        </div>
      `;
    }

    if (!response) {
      return html`
        <div class="gc-dock">
          <div class="gc-dock-header"><span class="gc-dock-title">Output</span></div>
          <div class="gc-dock-empty">Run a script (${/Mac/.test(navigator.platform) ? '⌘' : 'Ctrl+'}↵) to see output.</div>
        </div>
      `;
    }

    const table = parseTable(response.result);
    const error = !!response.exceptionStackTrace?.length;

    const extensionTabs = this.relevantExtensionTabs(response);
    const tabs: Array<{ id: DockTab; label: string; show: boolean }> = [
      { id: 'log', label: 'Log', show: !!response.output?.length },
      { id: 'result', label: 'Result', show: !table && !!response.result?.length },
      { id: 'table', label: 'Table', show: !!table },
      { id: 'trace', label: 'Trace', show: error },
      ...extensionTabs.map((tab) => ({ id: tab.id, label: tab.label, show: true })),
    ];
    const visibleTabs = tabs.filter((tab) => tab.show);
    const active = this.activeContent(response);
    const selectedExtensionTab = extensionTabs.find((tab) => tab.id === this.selectedTab);

    return html`
      <div class="gc-dock ${error ? 'gc-dock-error' : ''}">
        <div class="gc-dock-header">
          <span class="gc-dock-title">Output</span>
          <div class="gc-dock-tabs" role="tablist">
            ${visibleTabs.map(
              (tab) => html`
                <button
                  role="tab"
                  class="gc-dock-tab ${this.selectedTab === tab.id ? 'is-active' : ''}"
                  aria-selected=${this.selectedTab === tab.id}
                  @click=${() => (this.selectedTab = tab.id)}
                >
                  ${tab.label}
                </button>
              `,
            )}
          </div>
          ${response.runningTime?.length
            ? html`<sp-badge size="s" variant="informative">⏱ ${response.runningTime}</sp-badge>`
            : nothing}
          <span class="gc-status-spacer"></span>
          ${active
            ? html`
                <sp-action-button size="s" quiet @click=${() => this.copy(active.text)}>Copy</sp-action-button>
                <sp-action-button size="s" quiet @click=${() => this.download(active.text, active.fileName)}>
                  Download
                </sp-action-button>
              `
            : nothing}
        </div>
        <div class="gc-dock-body">
          ${selectedExtensionTab
            ? unsafeHTML(`<${selectedExtensionTab.element}></${selectedExtensionTab.element}>`)
            : this.selectedTab === 'table' && table
              ? this.renderTable(table)
              : this.selectedTab === 'trace' && active
                ? this.renderTrace(active.text)
                : active
                  ? html`<pre class="gc-dock-pre">${active.text}</pre>`
                  : visibleTabs.length === 0
                    ? html`<div class="gc-dock-empty">Script completed without output.</div>`
                    : nothing}
        </div>
      </div>
    `;
  }
}
