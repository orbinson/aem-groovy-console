import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { getJson } from '../api/client';
import { deleteScheduledJob, getScheduledJobs } from '../api/jobs-api';
import type { ScheduledJob } from '../api/types';
import { store } from '../state/store';

@customElement('gc-scheduled-jobs')
export class GcScheduledJobs extends LitElement {
  @state() private jobs: ScheduledJob[] = [];
  @state() private loaded = false;

  private refreshListener = (): void => void this.refresh();

  createRenderRoot(): this {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    window.addEventListener('gc-refresh-jobs', this.refreshListener);
    void this.refresh();
  }

  disconnectedCallback(): void {
    window.removeEventListener('gc-refresh-jobs', this.refreshListener);
    super.disconnectedCallback();
  }

  private async refresh(): Promise<void> {
    try {
      this.jobs = await getScheduledJobs();
    } catch {
      // non-critical
    } finally {
      this.loaded = true;
    }
  }

  private async edit(job: ScheduledJob): Promise<void> {
    try {
      const fullJob = await getJson<ScheduledJob>('/bin/groovyconsole/jobs.json', {
        scheduledJobId: job.scheduledJobId,
      });

      this.dispatchEvent(
        new CustomEvent('gc-edit-job', {
          detail: { job: { ...fullJob, scheduledJobId: job.scheduledJobId } },
          bubbles: true,
          composed: true,
        }),
      );
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch {
      store.showToast('Error loading scheduled job.', 'negative');
    }
  }

  private async delete(job: ScheduledJob): Promise<void> {
    try {
      await deleteScheduledJob(job.scheduledJobId);
      store.showToast('Scheduled job deleted successfully.');
      await this.refresh();
    } catch {
      store.showToast('Error deleting scheduled job.', 'negative');
    }
  }

  protected render() {
    if (!this.loaded) {
      return nothing;
    }

    return html`
      <div class="gc-drawer-content">

        ${this.jobs.length
          ? html`
              <table class="gc-table">
                <thead>
                  <tr>
                    <th>Job Title</th>
                    <th>Description</th>
                    <th>Script</th>
                    <th>Cron Expression</th>
                    <th>Next Execution Date</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  ${this.jobs.map(
                    (job) => html`
                      <tr>
                        <td>${job.jobTitle}</td>
                        <td>${job.jobDescription ?? ''}</td>
                        <td><code title=${job.script ?? ''}>${job.scriptPreview ?? ''}</code></td>
                        <td>${job.cronExpression ?? ''}</td>
                        <td>${job.nextExecutionDate ?? ''}</td>
                        <td class="gc-row-actions">
                          <sp-action-button size="s" quiet title="Edit Scheduled Job" @click=${() => this.edit(job)}>
                            Edit
                          </sp-action-button>
                          ${job.downloadUrl
                            ? html`
                                <sp-action-button
                                  size="s"
                                  quiet
                                  title="Download Last Execution Output"
                                  href=${job.downloadUrl}
                                  download
                                >
                                  Download
                                </sp-action-button>
                              `
                            : nothing}
                          <sp-action-button
                            size="s"
                            quiet
                            title="Delete Scheduled Job"
                            @click=${() => this.delete(job)}
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
          : html`<p class="gc-muted">No scheduled jobs found.</p>`}
      </div>
    `;
  }
}
