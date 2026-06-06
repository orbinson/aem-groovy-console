import { html, LitElement, nothing } from 'lit';
import { customElement, query, state } from 'lit/decorators.js';
import { ApiError } from '../api/client';
import { distributeScript, loadScript, pollExecution, runScriptAsync, saveScript } from '../api/console-api';
import { scheduleJob } from '../api/jobs-api';
import type { AuditRecord, ScheduledJob, ScheduleJobRequest } from '../api/types';
import { config } from '../config';
import { prefetchAssistData } from '../editor/assist-data';
import { syncMonacoTheme } from '../editor/monaco-setup';
import { persistence } from '../state/local-storage';
import { store, StoreController } from '../state/store';
import type { GcDataEditor } from './gc-data-editor';
import type { GcSaveDialog } from './gc-save-dialog';
import type { GcScheduler } from './gc-scheduler';
import type { GcScriptBrowserDialog } from './gc-script-browser-dialog';
import type { GcScriptEditor } from './gc-script-editor';

type DrawerId = 'history' | 'jobs' | 'help';
type EditorTab = 'script' | 'data';

@customElement('gc-app')
export class GcApp extends LitElement {
  private store = new StoreController(this);

  @state() private activeDrawer: DrawerId | null = null;
  @state() private editorTab: EditorTab = 'script';
  @state() private dataHasContent = persistence.getDataEditorContent().trim().length > 0;

  @query('gc-script-editor') private scriptEditor!: GcScriptEditor;
  @query('gc-data-editor') private dataEditor!: GcDataEditor;
  @query('gc-scheduler') private scheduler?: GcScheduler;
  @query('gc-script-browser-dialog') private scriptBrowser!: GcScriptBrowserDialog;
  @query('gc-save-dialog') private saveDialog!: GcSaveDialog;

  createRenderRoot(): this {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    syncMonacoTheme(this.store.state.colorScheme);

    this.addEventListener('gc-run', () => void this.execute('run'));
    this.addEventListener('gc-distribute', () => void this.execute('distribute'));
    this.addEventListener('gc-new', () => this.newScript());
    this.addEventListener('gc-download', () => this.downloadScript());
    this.addEventListener('gc-open', () => void this.scriptBrowser.show());
    this.addEventListener('gc-save', () => this.openSaveDialog());
    this.addEventListener('gc-toggle-theme', () => this.toggleColorScheme());
    this.addEventListener('gc-open-drawer', ((event: CustomEvent<{ drawer: DrawerId }>) => {
      this.activeDrawer = event.detail.drawer;
    }) as EventListener);
    this.addEventListener('gc-drawer-close', () => (this.activeDrawer = null));
    this.addEventListener('gc-data-changed', ((event: CustomEvent<{ hasContent: boolean }>) => {
      this.dataHasContent = event.detail.hasContent;
    }) as EventListener);
    this.addEventListener('gc-goto-line', ((event: CustomEvent<{ line: number }>) => {
      this.gotoLine(event.detail.line);
    }) as EventListener);
    this.addEventListener('gc-open-script', ((event: CustomEvent<{ path: string }>) => {
      void this.openScript(event.detail.path);
    }) as EventListener);
    this.addEventListener('gc-save-script', ((event: CustomEvent<{ fileName: string }>) => {
      void this.persistScript(event.detail.fileName);
    }) as EventListener);
    this.addEventListener('gc-load-record', ((event: CustomEvent<{ record: AuditRecord }>) => {
      this.loadAuditRecord(event.detail.record);
    }) as EventListener);
    this.addEventListener('gc-edit-job', ((event: CustomEvent<{ job: ScheduledJob }>) => {
      this.editScheduledJob(event.detail.job);
    }) as EventListener);
    this.addEventListener('gc-schedule', ((event: CustomEvent<{ request: ScheduleJobRequest; immediate: boolean }>) => {
      void this.schedule(event.detail.request, event.detail.immediate);
    }) as EventListener);

    // Global shortcuts: work everywhere in the app, not only with editor focus.
    // Monaco handles them itself when the editor is focused (and prevents default).
    window.addEventListener(
      'keydown',
      (event: KeyboardEvent) => {
        if (!(event.metaKey || event.ctrlKey) || event.defaultPrevented) {
          return;
        }
        if (event.key === 's') {
          event.preventDefault();
          this.openSaveDialog();
        } else if (event.key === 'Enter') {
          event.preventDefault();
          void this.execute('run');
        }
      },
      { capture: false },
    );
  }

  protected firstUpdated(): void {
    prefetchAssistData();

    // Deep link from the history panel / classic UI (?userId=...&script=...)
    const auditRecord = config.auditRecord;
    if (auditRecord) {
      this.scriptEditor.value = auditRecord.script ?? '';
      this.dataEditor.value = auditRecord.data ?? '';
      store.setState({ result: auditRecord, dirty: false });
    } else {
      store.setState({ dirty: false });
    }
  }

  private setRunning(running: boolean): void {
    store.setState({ running });
    this.scriptEditor.setReadOnly(running);
    this.dataEditor.setReadOnly(running);
  }

  private async execute(action: 'run' | 'distribute'): Promise<void> {
    if (this.store.state.running) {
      return;
    }

    const script = this.scriptEditor.value;

    if (!script.length) {
      store.showToast('Script is empty.', 'negative');
      return;
    }

    store.setState({ result: null });
    this.setRunning(true);

    try {
      const data = this.dataEditor.value;

      if (action === 'run') {
        // streaming execution: output appears live in the dock while the script runs
        const start = await runScriptAsync(script, data);

        if (!start.executionId) {
          // backend without streaming support answered synchronously with the full response
          store.setState({ result: start });
          return;
        }

        const executionId = start.executionId;
        store.setState({ liveOutput: '' });

        try {
          let offset = 0;
          for (;;) {
            const poll = await pollExecution(executionId, offset);

            if (poll.chunk) {
              store.setState({ liveOutput: (store.getState().liveOutput ?? '') + poll.chunk });
            }
            offset = poll.offset;

            if (poll.done) {
              store.setState({ result: poll.response ?? null, liveOutput: null });
              break;
            }

            await new Promise((resolve) => setTimeout(resolve, 500));
          }
        } catch (error) {
          // the execution lives in-memory on one instance; on clustered authors (AEMaaCS)
          // a re-routed poll can lose it while the script itself keeps running
          if (error instanceof ApiError && error.status === 404) {
            store.setState({ liveOutput: null });
            store.showToast(
              'Live output is no longer available — the script may still be running. Check History for the result.',
              'negative',
            );
            return;
          }
          throw error;
        }
      } else {
        const response = await distributeScript(script, data);
        store.setState({ result: response });
      }
    } catch (error) {
      store.setState({ liveOutput: null });

      if (error instanceof ApiError && error.status === 403) {
        store.showToast('You do not have permission to run scripts in the Groovy Console.', 'negative');
      } else {
        store.showToast(
          action === 'run'
            ? 'Script execution failed. Check error.log file.'
            : 'Script distribution failed. Check error.log file.',
          'negative',
        );
      }
    } finally {
      this.setRunning(false);
      window.dispatchEvent(new CustomEvent('gc-refresh-audit'));
    }
  }

  private newScript(): void {
    persistence.clearScriptName();
    this.scriptEditor.value = '';
    this.dataEditor.value = '';
    this.editorTab = 'script';
    store.setState({ result: null, scriptName: '', dirty: false });
  }

  private downloadScript(): void {
    const script = this.scriptEditor.value;

    if (!script.length) {
      store.showToast('Script is empty.', 'negative');
      return;
    }

    const fileName = `${this.store.state.scriptName || 'script'}.groovy`;
    const blob = new Blob([script], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(url);
  }

  private openSaveDialog(): void {
    if (!this.scriptEditor.value.length) {
      store.showToast('Script is empty.', 'negative');
      return;
    }
    this.saveDialog.show();
  }

  private async openScript(path: string): Promise<void> {
    try {
      const script = await loadScript(path);

      persistence.saveScriptName(path);
      this.scriptEditor.value = script;
      this.editorTab = 'script';
      store.setState({ result: null, scriptName: persistence.getScriptName(), dirty: false });
      store.showToast('Script loaded successfully.');
    } catch {
      store.showToast('Load failed, check error.log file.', 'negative');
    }
  }

  private async persistScript(fileName: string): Promise<void> {
    try {
      await saveScript(fileName, this.scriptEditor.value);

      persistence.saveScriptName(fileName);
      store.setState({ scriptName: persistence.getScriptName(), dirty: false });
      store.showToast('Script saved successfully.');
    } catch {
      store.showToast('Save failed, check error.log file.', 'negative');
    }
  }

  private loadAuditRecord(record: AuditRecord): void {
    this.scriptEditor.value = record.script ?? '';
    this.dataEditor.value = record.data ?? '';
    this.editorTab = 'script';
    this.activeDrawer = null;
    store.setState({ result: record, dirty: false });
  }

  private async editScheduledJob(job: ScheduledJob): Promise<void> {
    this.scriptEditor.value = job.script ?? '';
    this.dataEditor.value = job.data ?? '';
    this.editorTab = 'script';
    this.activeDrawer = 'jobs';
    store.setState({ result: null });

    await this.updateComplete;
    this.scheduler?.populate({
      scheduledJobId: job.scheduledJobId ?? '',
      jobTitle: job.jobTitle ?? '',
      jobDescription: job.jobDescription ?? '',
      cronExpression: job.cronExpression ?? '',
      mediaType: job.mediaType || 'text/plain',
      emailTo: job.emailTo ?? '',
    });
  }

  private async schedule(request: ScheduleJobRequest, immediate: boolean): Promise<void> {
    if (!request.jobTitle?.length) {
      store.showToast('Job Title is required.', 'negative');
      return;
    }

    if (!immediate && !request.cronExpression?.length) {
      store.showToast('Cron Expression is required if job is not immediate.', 'negative');
      return;
    }

    const script = this.scriptEditor.value;

    if (!script.length) {
      store.showToast('Script is empty.', 'negative');
      return;
    }

    this.setRunning(true);

    try {
      await scheduleJob({ ...request, script, data: this.dataEditor.value });

      store.showToast('Job scheduled successfully.');
      this.scheduler?.clear();
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        store.showToast('Invalid Cron expression.', 'negative');
      } else if (error instanceof ApiError && error.status === 403) {
        store.showToast('You do not have permission to schedule jobs in the Groovy Console.', 'negative');
      } else {
        store.showToast('Job scheduling failed. Check error.log file.', 'negative');
      }
    } finally {
      this.setRunning(false);
      window.dispatchEvent(new CustomEvent('gc-refresh-jobs'));
    }
  }

  private toggleColorScheme(): void {
    const colorScheme = this.store.state.colorScheme === 'dark' ? 'light' : 'dark';
    persistence.saveColorScheme(colorScheme);
    store.setState({ colorScheme });
    syncMonacoTheme(colorScheme);
  }

  private toggleDrawer(drawer: DrawerId): void {
    this.activeDrawer = this.activeDrawer === drawer ? null : drawer;
  }

  /** Jump to a script line from a stacktrace frame link. */
  private gotoLine(line: number): void {
    this.editorTab = 'script';

    const editor = this.scriptEditor.monacoEditor;
    if (editor) {
      editor.revealLineInCenter(line);
      editor.setPosition({ lineNumber: line, column: 1 });
      editor.focus();
    }
  }

  private onSplitterDown(event: PointerEvent): void {
    event.preventDefault();

    const handle = event.currentTarget as HTMLElement;
    const pane = this.querySelector<HTMLDivElement>('.gc-editor-pane')!;
    const container = this.querySelector<HTMLDivElement>('.gc-split')!;
    const startY = event.clientY;
    const startHeight = pane.offsetHeight;

    const onMove = (move: PointerEvent): void => {
      const max = container.offsetHeight - 80; // keep the output dock reachable
      const height = Math.min(Math.max(startHeight + (move.clientY - startY), 120), max);
      pane.style.flexBasis = `${height}px`;
    };

    const onUp = (): void => {
      handle.removeEventListener('pointermove', onMove);
      handle.removeEventListener('pointerup', onUp);
      persistence.saveSplitterPosition(pane.offsetHeight);
    };

    handle.setPointerCapture(event.pointerId);
    handle.addEventListener('pointermove', onMove);
    handle.addEventListener('pointerup', onUp);
  }

  protected render() {
    const { colorScheme, toast } = this.store.state;
    const showJobsRail = config.hasScheduledJobPermission || config.activeJobs.length > 0;
    // default: editor gets ~72% of the workspace height; persisted px size wins
    const persistedSplit = persistence.getSplitterPosition();
    const primarySize = persistedSplit ? `${persistedSplit}px` : '72%';

    return html`
      <sp-theme class="gc-root-theme" system="spectrum" color=${colorScheme} scale="medium">
        <div class="gc-frame">
          <gc-app-bar></gc-app-bar>

          <div class="gc-workspace">
            <nav class="gc-rail" aria-label="Panels">
              ${config.auditEnabled
                ? html`
                    <button
                      class="gc-rail-button ${this.activeDrawer === 'history' ? 'is-active' : ''}"
                      title="History"
                      aria-label="History"
                      @click=${() => this.toggleDrawer('history')}
                    >
                      <sp-icon-history></sp-icon-history>
                    </button>
                  `
                : nothing}
              ${showJobsRail
                ? html`
                    <button
                      class="gc-rail-button ${this.activeDrawer === 'jobs' ? 'is-active' : ''}"
                      title="Scheduled Jobs"
                      aria-label="Scheduled Jobs"
                      @click=${() => this.toggleDrawer('jobs')}
                    >
                      <sp-icon-clock></sp-icon-clock>
                      ${config.activeJobs.length
                        ? html`<span class="gc-rail-badge">${config.activeJobs.length}</span>`
                        : nothing}
                    </button>
                  `
                : nothing}
              <button
                class="gc-rail-button ${this.activeDrawer === 'help' ? 'is-active' : ''}"
                title="Help &amp; Reference"
                aria-label="Help and Reference"
                @click=${() => this.toggleDrawer('help')}
              >
                <sp-icon-help></sp-icon-help>
              </button>
            </nav>

            <div class="gc-split">
              <div class="gc-editor-pane" style="flex-basis: ${primarySize}">
                <div class="gc-pane-header">
                  <button
                    class="gc-pane-tab ${this.editorTab === 'script' ? 'is-active' : ''}"
                    @click=${() => (this.editorTab = 'script')}
                  >
                    Script
                  </button>
                  <button
                    class="gc-pane-tab ${this.editorTab === 'data' ? 'is-active' : ''}"
                    @click=${() => (this.editorTab = 'data')}
                  >
                    Data${this.dataHasContent ? html`<span class="gc-dirty-dot">●</span>` : nothing}
                  </button>
                </div>
                <gc-script-editor class=${this.editorTab === 'script' ? '' : 'gc-hidden'}></gc-script-editor>
                <gc-data-editor class=${this.editorTab === 'data' ? '' : 'gc-hidden'}></gc-data-editor>
              </div>

              <div
                class="gc-split-handle"
                role="separator"
                aria-orientation="horizontal"
                aria-label="Resize editor and output"
                @pointerdown=${this.onSplitterDown}
              ></div>

              <gc-result class="gc-output"></gc-result>
            </div>
          </div>

          <gc-status-bar></gc-status-bar>

          <gc-drawer
            heading="History"
            ?open=${this.activeDrawer === 'history'}
          >
            ${this.activeDrawer === 'history' ? html`<gc-history></gc-history>` : nothing}
          </gc-drawer>

          <gc-drawer heading="Scheduled Jobs" ?open=${this.activeDrawer === 'jobs'}>
            ${this.activeDrawer === 'jobs'
              ? html`
                  ${config.hasScheduledJobPermission
                    ? html`
                        <gc-scheduler></gc-scheduler>
                        <h4 class="gc-drawer-subheading">Scheduled jobs</h4>
                        <gc-scheduled-jobs></gc-scheduled-jobs>
                      `
                    : nothing}
                  ${config.activeJobs.length
                    ? html`
                        <h4 class="gc-drawer-subheading">Active Jobs</h4>
                        <gc-active-jobs></gc-active-jobs>
                      `
                    : nothing}
                `
              : nothing}
          </gc-drawer>

          <gc-drawer heading="Help &amp; Reference" ?open=${this.activeDrawer === 'help'}>
            ${this.activeDrawer === 'help' ? html`<gc-reference></gc-reference>` : nothing}
          </gc-drawer>

          <gc-script-browser-dialog></gc-script-browser-dialog>
          <gc-save-dialog></gc-save-dialog>

          ${toast
            ? html`
                <sp-toast
                  class="gc-toast"
                  open
                  variant=${toast.variant}
                  timeout="4000"
                  @close=${() => store.setState({ toast: null })}
                >
                  ${toast.message}
                </sp-toast>
              `
            : nothing}
        </div>
      </sp-theme>
    `;
  }
}
