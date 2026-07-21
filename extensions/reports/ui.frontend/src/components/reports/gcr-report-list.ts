import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { ApiError } from '@console/api/client';
import { listReports } from '../../api/reports-api';
import type { ReportSummary } from '../../api/reports-types';

type CatalogueView = 'cards' | 'list';

const VIEW_STORAGE_KEY = 'gcr-reports-view';

/** Business-facing report catalogue: searchable, grouped by category, as cards or a compact list. */
@customElement('gcr-report-list')
export class GcrReportList extends LitElement {
  @state() private reports: ReportSummary[] = [];
  @state() private canManage = false;
  @state() private filter = '';
  @state() private loaded = false;
  @state() private error: string | null = null;
  @state() private view: CatalogueView = localStorage.getItem(VIEW_STORAGE_KEY) === 'list' ? 'list' : 'cards';

  createRenderRoot(): this {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    void this.refresh();
  }

  private async refresh(): Promise<void> {
    try {
      const response = await listReports();
      this.reports = response.reports;
      this.canManage = response.canManage;
    } catch (error) {
      this.error =
        error instanceof ApiError && error.status === 404
          ? 'The reports extension is not installed on this instance.'
          : 'Could not load reports.';
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

  private setView(view: CatalogueView): void {
    this.view = view;
    localStorage.setItem(VIEW_STORAGE_KEY, view);
  }

  private get categories(): Array<{ category: string; reports: ReportSummary[] }> {
    const groups = new Map<string, ReportSummary[]>();
    for (const report of this.filtered) {
      const category = report.category?.trim() || 'General';
      if (!groups.has(category)) {
        groups.set(category, []);
      }
      groups.get(category)!.push(report);
    }
    return [...groups.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([category, reports]) => ({ category, reports }));
  }

  protected render() {
    if (!this.loaded) {
      return html`<div class="gcr-loading" role="status">
        <sp-progress-circle indeterminate size="l"></sp-progress-circle>
        <span class="gcr-visually-hidden">Loading reports…</span>
      </div>`;
    }

    if (this.error) {
      return html`<div class="gcr-empty" role="alert">${this.error}</div>`;
    }

    return html`
      <div class="gcr-page">
        <div class="gcr-page-header">
          <h1>Reports</h1>
          <div class="gcr-page-header-actions">
            <sp-search
              placeholder="Search reports"
              .value=${this.filter}
              @input=${(event: Event) => (this.filter = (event.target as HTMLInputElement).value)}
              @submit=${(event: Event) => event.preventDefault()}
            ></sp-search>
            <div class="gcr-view-toggle" role="group" aria-label="View">
              <sp-action-button
                size="m"
                toggles
                ?selected=${this.view === 'cards'}
                title="Card view"
                aria-label="Card view"
                @click=${() => this.setView('cards')}
              >
                Cards
              </sp-action-button>
              <sp-action-button
                size="m"
                toggles
                ?selected=${this.view === 'list'}
                title="List view"
                aria-label="List view"
                @click=${() => this.setView('list')}
              >
                List
              </sp-action-button>
            </div>
            ${this.canManage
              ? html`
                  <sp-button
                    @click=${() =>
                      this.dispatchEvent(
                        new CustomEvent('gcr-navigate', {
                          detail: { hash: '#/new' },
                          bubbles: true,
                          composed: true,
                        }),
                      )}
                  >
                    New report
                  </sp-button>
                `
              : nothing}
          </div>
        </div>

        ${this.filtered.length === 0
          ? html`<div class="gcr-empty">
              ${this.reports.length === 0
                ? 'No reports are available for your account.'
                : 'No reports match your search.'}
            </div>`
          : this.view === 'list'
            ? this.renderList()
            : this.renderCards()}
      </div>
    `;
  }

  private editButton(report: ReportSummary) {
    if (!report.canEdit) {
      return nothing;
    }
    return html`
      <sp-action-button
        size="s"
        quiet
        @click=${(event: Event) => {
          event.preventDefault();
          event.stopPropagation();
          window.location.hash = `#/report/${encodeURIComponent(report.name)}/edit`;
        }}
      >
        Edit
      </sp-action-button>
    `;
  }

  private renderCards() {
    return this.categories.map(
      ({ category, reports }) => html`
        <h2 class="gcr-category">${category}</h2>
        <div class="gcr-card-grid">
          ${reports.map(
            (report) => html`
              <a class="gcr-card" href="#/report/${encodeURIComponent(report.name)}">
                <div class="gcr-card-title">${report.title}</div>
                ${report.description
                  ? html`<div class="gcr-card-description">${report.description}</div>`
                  : nothing}
                <div class="gcr-card-footer">${this.editButton(report)}</div>
              </a>
            `,
          )}
        </div>
      `,
    );
  }

  private renderList() {
    return this.categories.map(
      ({ category, reports }) => html`
        <h2 class="gcr-category">${category}</h2>
        <div class="gcr-list">
          ${reports.map(
            (report) => html`
              <a class="gcr-list-row" href="#/report/${encodeURIComponent(report.name)}">
                <span class="gcr-list-title">${report.title}</span>
                <span class="gcr-list-description">${report.description ?? ''}</span>
                <span class="gcr-list-actions">${this.editButton(report)}</span>
              </a>
            `,
          )}
        </div>
      `,
    );
  }
}
