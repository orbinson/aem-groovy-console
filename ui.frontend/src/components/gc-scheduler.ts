import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import type { ScheduleJobRequest } from '../api/types';
import { config } from '../config';

export interface SchedulerFormValues {
  scheduledJobId: string;
  jobTitle: string;
  jobDescription: string;
  immediate: boolean;
  cronExpression: string;
  mediaType: string;
  emailTo: string;
}

const EMPTY_FORM: SchedulerFormValues = {
  scheduledJobId: '',
  jobTitle: '',
  jobDescription: '',
  immediate: false,
  cronExpression: '',
  mediaType: 'text/plain',
  emailTo: '',
};

/**
 * Scheduler form shown inline at the top of the jobs drawer; emits gc-schedule with the
 * job request (script/data added by gc-app). populate() pre-fills it when editing a job.
 */
@customElement('gc-scheduler')
export class GcScheduler extends LitElement {
  @state() private form: SchedulerFormValues = { ...EMPTY_FORM };

  createRenderRoot(): this {
    return this;
  }

  populate(values: Partial<SchedulerFormValues>): void {
    this.form = { ...EMPTY_FORM, ...values };
  }

  clear(): void {
    this.form = { ...EMPTY_FORM };
  }

  private setField<K extends keyof SchedulerFormValues>(key: K, value: SchedulerFormValues[K]): void {
    this.form = { ...this.form, [key]: value };
  }

  private submit(): void {
    const request: Partial<ScheduleJobRequest> = {
      jobTitle: this.form.jobTitle,
      jobDescription: this.form.jobDescription,
      cronExpression: this.form.immediate ? '' : this.form.cronExpression,
      mediaType: this.form.mediaType,
      emailTo: this.form.emailTo,
      scheduledJobId: this.form.scheduledJobId,
    };

    this.dispatchEvent(
      new CustomEvent('gc-schedule', {
        detail: { request, immediate: this.form.immediate },
        bubbles: true,
        composed: true,
      }),
    );
  }

  protected render() {
    return html`
      <div class="gc-scheduler-form">
        <h4 class="gc-drawer-subheading">
          ${this.form.scheduledJobId ? 'Edit scheduled job' : 'Schedule current script'}
        </h4>
        <p class="gc-muted">
          Scripts can be run immediately or scheduled using a Cron expression. Due to the job-based nature of
          scheduled script execution, <code>slingRequest</code> and <code>slingResponse</code> bindings are
          unavailable.
        </p>

        <div class="gc-form">
          <sp-field-label for="gc-job-title" required>Job Title</sp-field-label>
          <sp-textfield
            id="gc-job-title"
            placeholder="Job Title"
            .value=${this.form.jobTitle}
            @input=${(event: Event) => this.setField('jobTitle', (event.target as HTMLInputElement).value)}
          ></sp-textfield>

          <sp-field-label for="gc-job-description">Job Description</sp-field-label>
          <sp-textfield
            id="gc-job-description"
            placeholder="Job Description"
            .value=${this.form.jobDescription}
            @input=${(event: Event) => this.setField('jobDescription', (event.target as HTMLInputElement).value)}
          ></sp-textfield>

          <sp-checkbox
            ?checked=${this.form.immediate}
            @change=${(event: Event) => this.setField('immediate', (event.target as HTMLInputElement).checked)}
          >
            Immediate
          </sp-checkbox>

          <sp-field-label for="gc-cron-expression">Cron Expression</sp-field-label>
          <sp-textfield
            id="gc-cron-expression"
            placeholder="0 0 * * * ?"
            ?disabled=${this.form.immediate}
            .value=${this.form.cronExpression}
            @input=${(event: Event) => this.setField('cronExpression', (event.target as HTMLInputElement).value)}
          ></sp-textfield>

          <sp-field-label for="gc-media-type">Media Type</sp-field-label>
          <sp-picker
            id="gc-media-type"
            value=${this.form.mediaType}
            @change=${(event: Event) => this.setField('mediaType', (event.target as HTMLSelectElement).value)}
          >
            <sp-menu-item value="text/plain">Plain Text</sp-menu-item>
            <sp-menu-item value="text/csv">CSV</sp-menu-item>
            <sp-menu-item value="text/xml">XML</sp-menu-item>
            <sp-menu-item value="text/html">HTML</sp-menu-item>
          </sp-picker>
          <sp-help-text size="s">Select the media type to use when downloading script output.</sp-help-text>

          ${config.emailEnabled
            ? html`
                <sp-field-label for="gc-email-to">Email To</sp-field-label>
                <sp-textfield
                  id="gc-email-to"
                  placeholder="Email To"
                  .value=${this.form.emailTo}
                  @input=${(event: Event) => this.setField('emailTo', (event.target as HTMLInputElement).value)}
                ></sp-textfield>
                <sp-help-text size="s">Comma-delimited list of email addresses to receive script output.</sp-help-text>
              `
            : nothing}

          <div>
            <sp-button variant="primary" @click=${this.submit}>Schedule Job</sp-button>
          </div>
        </div>
      </div>
    `;
  }
}
