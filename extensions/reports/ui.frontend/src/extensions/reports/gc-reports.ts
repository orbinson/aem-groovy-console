import { css, html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { listReports, reportsPageUrl } from '../../api/reports-api';
import type { ReportSummary } from '../../api/reports-types';

/**
 * Developer-focused reports panel, registered in the console's activity rail by the reports UI
 * extension module (reports-console-panel.ts).
 *
 * Self-contained (shadow DOM + own styles); links out to the standalone reports UI to open or edit a
 * report. Report scripts run there (or in the editor's "try it out"), not in the plain console — they
 * rely on the report/params bindings the console doesn't provide.
 */
@customElement('gc-reports')
export class GcReports extends LitElement {
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

    .toolbar sp-search {
      flex: 1;
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
      padding: 8px;
      border-radius: 6px;
      border-bottom: 1px solid var(--spectrum-gray-200);
    }

    li:hover {
      background: var(--spectrum-gray-100);
    }

    .item-main {
      display: flex;
      flex-direction: column;
      gap: 3px;
      min-width: 0;
    }

    /* title + category chip on one row; the badge keeps its natural size instead of
       stretching to fill the column */
    .item-header {
      display: flex;
      align-items: center;
      gap: 8px;
      min-width: 0;
    }

    .item-title {
      font-weight: 600;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .item-category {
      flex-shrink: 0;
    }

    .item-description {
      font-size: 12px;
      color: var(--spectrum-gray-600);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .item-actions {
      display: flex;
      align-items: center;
      gap: 2px;
      flex-shrink: 0;
    }
  `;

  @state() private reports: ReportSummary[] = [];
  @state() private canManage = false;
  @state() private filter = '';
  @state() private loaded = false;
  @state() private error: string | null = null;

  connectedCallback(): void {
    super.connectedCallback();
    void this.refresh();
  }

  private async refresh(): Promise<void> {
    this.loaded = false;
    try {
      const response = await listReports();
      this.reports = response.reports;
      this.canManage = response.canManage;
      this.error = null;
    } catch {
      this.error = 'Could not load reports. Is the reports extension installed?';
    } finally {
      this.loaded = true;
    }
  }

  private get filtered(): ReportSummary[] {
    const filter = this.filter.trim().toLowerCase();
    if (!filter) {
      return this.reports;
    }
    return this.reports.filter((report) =>
      [report.title, report.name, report.description ?? '', report.category ?? '']
        .join(' ')
        .toLowerCase()
        .includes(filter),
    );
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
        <sp-search
          placeholder="Filter reports"
          .value=${this.filter}
          @input=${(event: Event) => (this.filter = (event.target as HTMLInputElement).value)}
          @submit=${(event: Event) => event.preventDefault()}
        ></sp-search>
        <sp-action-button size="s" quiet @click=${() => void this.refresh()} title="Refresh" aria-label="Refresh">
          ↻
        </sp-action-button>
        ${this.canManage
          ? html`
              <sp-button size="s" variant="secondary" href=${reportsPageUrl('#/new')} target="_blank">
                New report
              </sp-button>
            `
          : nothing}
      </div>

      ${this.filtered.length === 0
        ? html`<p class="empty">
            ${this.reports.length === 0 ? 'No reports available.' : 'No reports match your filter.'}
          </p>`
        : html`
            <ul>
              ${this.filtered.map(
                (report) => html`
                  <li>
                    <div class="item-main">
                      <div class="item-header">
                        <span class="item-title">${report.title}</span>
                        ${report.category
                          ? html`<sp-badge class="item-category" size="s" variant="neutral">
                              ${report.category}
                            </sp-badge>`
                          : nothing}
                      </div>
                      ${report.description
                        ? html`<span class="item-description">${report.description}</span>`
                        : nothing}
                    </div>
                    <div class="item-actions">
                      <sp-action-button
                        size="s"
                        href=${reportsPageUrl(`#/report/${encodeURIComponent(report.name)}`)}
                        target="_blank"
                        title="Open in the reports UI"
                      >
                        Open
                      </sp-action-button>
                      ${report.canEdit
                        ? html`
                            <sp-action-button
                              size="s"
                              quiet
                              href=${reportsPageUrl(`#/report/${encodeURIComponent(report.name)}/edit`)}
                              target="_blank"
                              title="Edit in the reports UI"
                            >
                              Edit
                            </sp-action-button>
                          `
                        : nothing}
                    </div>
                  </li>
                `,
              )}
            </ul>
          `}
    `;
  }
}
