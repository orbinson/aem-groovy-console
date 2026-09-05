import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { deleteAllAuditRecords, deleteAuditRecord, getAuditRecords } from '../api/audit-api';
import { getJson } from '../api/client';
import type { AuditRecord } from '../api/types';
import { config } from '../config';
import { store } from '../state/store';

@customElement('gc-history')
export class GcHistory extends LitElement {
  @state() private records: AuditRecord[] = [];
  @state() private filter = '';
  @state() private startDate = '';
  @state() private endDate = '';
  @state() private confirmDeleteAll = false;
  @state() private loaded = false;

  private refreshListener = (): void => void this.refresh();
  /** Monotonic counter so a slow response from an earlier date-filter change can't overwrite a newer one. */
  private refreshSeq = 0;

  createRenderRoot(): this {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    window.addEventListener('gc-refresh-audit', this.refreshListener);
    void this.refresh();
  }

  disconnectedCallback(): void {
    window.removeEventListener('gc-refresh-audit', this.refreshListener);
    super.disconnectedCallback();
  }

  private async refresh(): Promise<void> {
    const seq = ++this.refreshSeq;
    try {
      const records =
        this.startDate && this.endDate
          ? await getAuditRecords(this.startDate, this.endDate)
          : await getAuditRecords();

      // ignore a response superseded by a newer refresh (e.g. rapid From/To edits)
      if (seq === this.refreshSeq) {
        this.records = records;
      }
    } catch {
      // history is non-critical; ignore load failures (e.g. on permission loss)
    } finally {
      if (seq === this.refreshSeq) {
        this.loaded = true;
      }
    }
  }

  private async loadRecord(record: AuditRecord): Promise<void> {
    try {
      const fullRecord = await getJson<AuditRecord>('/bin/groovyconsole/audit.json', {
        userId: record.userId,
        script: record.relativePath,
      });

      this.dispatchEvent(
        new CustomEvent('gc-load-record', { detail: { record: fullRecord }, bubbles: true, composed: true }),
      );
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch {
      store.showToast('Error loading audit record.', 'negative');
    }
  }

  private async deleteRecord(record: AuditRecord): Promise<void> {
    try {
      await deleteAuditRecord(record.userId, record.relativePath);
      store.showToast('Audit record deleted successfully.');
      await this.refresh();
    } catch {
      store.showToast('Error deleting audit record.', 'negative');
    }
  }

  private async deleteAll(): Promise<void> {
    this.confirmDeleteAll = false;

    try {
      await deleteAllAuditRecords();
      store.showToast('Audit records deleted successfully.');
      await this.refresh();
    } catch {
      store.showToast('Error deleting audit records.', 'negative');
    }
  }

  private onDateChange(): void {
    void this.refresh();
  }

  private clearDates(): void {
    this.startDate = '';
    this.endDate = '';
    void this.refresh();
  }

  private get filteredRecords(): AuditRecord[] {
    const filter = this.filter.toLowerCase();

    return filter
      ? this.records.filter((record) =>
          [record.jobTitle, record.script, record.scriptPreview, record.exception, record.date]
            .filter(Boolean)
            .some((value) => String(value).toLowerCase().includes(filter)),
        )
      : this.records;
  }

  protected render() {
    if (!this.loaded) {
      return nothing;
    }

    const records = this.filteredRecords;

    return html`
      <div class="gc-drawer-content">

        <div class="gc-panel-toolbar">
          <sp-search
            placeholder="Contains"
            size="s"
            .value=${this.filter}
            @input=${(event: Event) => (this.filter = (event.target as HTMLInputElement).value)}
          ></sp-search>
          <sp-field-label for="gc-history-start" size="s">From</sp-field-label>
          <input
            id="gc-history-start"
            class="gc-date-input"
            type="date"
            .value=${this.startDate}
            @change=${(event: Event) => {
              this.startDate = (event.target as HTMLInputElement).value;
              this.onDateChange();
            }}
          />
          <sp-field-label for="gc-history-end" size="s">To</sp-field-label>
          <input
            id="gc-history-end"
            class="gc-date-input"
            type="date"
            .value=${this.endDate}
            @change=${(event: Event) => {
              this.endDate = (event.target as HTMLInputElement).value;
              this.onDateChange();
            }}
          />
          <sp-action-button size="s" quiet @click=${this.clearDates}>Clear</sp-action-button>
          ${this.records.length
            ? html`
                <sp-button
                  size="s"
                  variant="negative"
                  treatment="outline"
                  style="margin-inline-start: auto;"
                  @click=${() => (this.confirmDeleteAll = true)}
                >
                  Delete All
                </sp-button>
              `
            : nothing}
        </div>

        ${records.length
          ? html`
              <table class="gc-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Job Title</th>
                    <th>Script</th>
                    <th>Exception</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  ${records.map(
                    (record) => html`
                      <tr>
                        <td>
                          <a href="${config.contextPath}/groovyconsole${record.queryString ?? ''}">${record.date}</a>
                        </td>
                        <td>${record.jobTitle ?? ''}</td>
                        <td><code title=${record.script ?? ''}>${record.scriptPreview ?? ''}</code></td>
                        <td>
                          ${record.exception
                            ? html`<sp-badge size="s" variant="negative" title=${record.exception}>
                                ${record.exception.split('.').pop()}
                              </sp-badge>`
                            : nothing}
                        </td>
                        <td class="gc-row-actions">
                          <sp-action-button size="s" quiet title="Load Script" @click=${() => this.loadRecord(record)}>
                            Load
                          </sp-action-button>
                          ${record.downloadUrl
                            ? html`
                                <sp-action-button
                                  size="s"
                                  quiet
                                  title="Download Output"
                                  href=${record.downloadUrl}
                                  download
                                >
                                  Download
                                </sp-action-button>
                              `
                            : nothing}
                          <sp-action-button
                            size="s"
                            quiet
                            title="Delete Record"
                            @click=${() => this.deleteRecord(record)}
                          >
                            Delete
                          </sp-action-button>
                        </td>
                      </tr>
                    `,
                  )}
                </tbody>
              </table>
            `
          : html`<p class="gc-muted">No audit records found.</p>`}

        ${this.confirmDeleteAll
          ? html`
              <sp-dialog-wrapper
                open
                underlay
                headline="Delete all audit records?"
                confirm-label="Delete All"
                cancel-label="Cancel"
                @confirm=${this.deleteAll}
                @cancel=${() => (this.confirmDeleteAll = false)}
              >
                This will permanently delete all audit records${config.aem ? ' for the current user' : ''}.
              </sp-dialog-wrapper>
            `
          : nothing}
      </div>
    `;
  }
}
