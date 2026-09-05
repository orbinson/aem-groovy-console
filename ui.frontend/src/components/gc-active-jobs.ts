import { html, LitElement } from 'lit';
import { customElement } from 'lit/decorators.js';
import { config } from '../config';

@customElement('gc-active-jobs')
export class GcActiveJobs extends LitElement {
  createRenderRoot(): this {
    return this;
  }

  protected render() {
    return html`
      <div class="gc-drawer-content">

        <table class="gc-table">
          <thead>
            <tr>
              <th>Start Time</th>
              <th>Title</th>
              <th>Description</th>
              <th>Script</th>
            </tr>
          </thead>
          <tbody>
            ${config.activeJobs.map(
              (job) => html`
                <tr>
                  <td>${job.startTime ?? ''}</td>
                  <td>${job.title ?? ''}</td>
                  <td>${job.description ?? ''}</td>
                  <td><code>${job.script ?? ''}</code></td>
                </tr>
              `,
            )}
          </tbody>
        </table>
      </div>
    `;
  }
}
