import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { listScriptsFolder, SCRIPTS_FOLDER, type FolderListing } from '../api/console-api';
import { persistence } from '../state/local-storage';
import { mutePlaceholders } from '../util/mute-placeholders';

/**
 * Save-as dialog: folder-navigable target picker (same breadcrumb + folder list as the open dialog)
 * plus a file name field; emits gc-save-script with the folder-relative file name.
 */
@customElement('gc-save-dialog')
export class GcSaveDialog extends LitElement {
  @state() private open = false;
  @state() private fileName = '';
  @state() private currentPath = SCRIPTS_FOLDER;
  @state() private listing: FolderListing = { folders: [], files: [] };
  @state() private loading = false;

  createRenderRoot(): this {
    return this;
  }

  // sp-dialog-wrapper only handles Escape when opened through the Overlay system; rendered inline it
  // does not, so close on Escape ourselves (same pattern as gc-drawer).
  private keyListener = (event: KeyboardEvent): void => {
    if (event.key === 'Escape' && this.open) {
      this.open = false;
    }
  };

  connectedCallback(): void {
    super.connectedCallback();
    // capture phase: the Spectrum dialog stops Escape from bubbling
    window.addEventListener('keydown', this.keyListener, { capture: true });
  }

  disconnectedCallback(): void {
    window.removeEventListener('keydown', this.keyListener, { capture: true });
    super.disconnectedCallback();
  }

  protected updated(): void {
    mutePlaceholders(this);
  }

  show(): void {
    this.fileName = persistence.getScriptName() || '';
    this.open = true;
    void this.navigate(persistence.getSaveFolder() || SCRIPTS_FOLDER);
  }

  private async navigate(path: string): Promise<void> {
    this.currentPath = path;
    this.loading = true;

    try {
      this.listing = await listScriptsFolder(path);
    } catch {
      // folder may not exist yet (e.g. remembered folder was deleted) — fall back to the root
      if (path !== SCRIPTS_FOLDER) {
        await this.navigate(SCRIPTS_FOLDER);
        return;
      }
      this.listing = { folders: [], files: [] };
    } finally {
      this.loading = false;
    }
  }

  private get breadcrumbs(): Array<{ label: string; path: string }> {
    const relative = this.currentPath.slice(SCRIPTS_FOLDER.length).split('/').filter(Boolean);
    const crumbs = [{ label: 'Scripts', path: SCRIPTS_FOLDER }];

    let path = SCRIPTS_FOLDER;
    for (const segment of relative) {
      path = `${path}/${segment}`;
      crumbs.push({ label: segment, path });
    }

    return crumbs;
  }

  /** The file name to submit: current folder (relative to the scripts root) + typed name. */
  private get relativeFileName(): string {
    const folder = this.currentPath.slice(SCRIPTS_FOLDER.length).replace(/^\//, '');
    const name = this.fileName.trim().replace(/^\/+/, '');
    return folder && name ? `${folder}/${name}` : name;
  }

  private confirm(): void {
    let fileName = this.relativeFileName;

    if (!fileName) {
      return;
    }

    if (!fileName.endsWith('.groovy')) {
      fileName = `${fileName}.groovy`;
    }

    this.open = false;
    persistence.saveSaveFolder(this.currentPath);
    this.dispatchEvent(
      new CustomEvent('gc-save-script', { detail: { fileName }, bubbles: true, composed: true }),
    );
  }

  protected render() {
    if (!this.open) {
      return nothing;
    }

    const crumbs = this.breadcrumbs;
    const name = this.fileName.trim().replace(/^\/+/, '');
    const targetPath = name
      ? `${this.currentPath}/${name}${name.endsWith('.groovy') ? '' : '.groovy'}`
      : '';

    return html`
      <sp-dialog-wrapper
        open
        underlay
        headline="Save Script"
        confirm-label="Save"
        cancel-label="Cancel"
        @confirm=${this.confirm}
        @cancel=${() => (this.open = false)}
        @close=${() => (this.open = false)}
      >
        <nav class="gc-browser-breadcrumbs" aria-label="Folder path">
          ${crumbs.map(
            (crumb, index) => html`
              ${index > 0 ? html`<span class="gc-browser-crumb-sep">/</span>` : nothing}
              ${index === crumbs.length - 1
                ? html`<span class="gc-browser-crumb is-current">${crumb.label}</span>`
                : html`
                    <button class="gc-browser-crumb" @click=${() => void this.navigate(crumb.path)}>
                      ${crumb.label}
                    </button>
                  `}
            `,
          )}
        </nav>

        <div class="gc-browser-list gc-save-folder-list">
          ${this.loading
            ? html`<sp-progress-circle indeterminate size="m" aria-label="Loading folders"></sp-progress-circle>`
            : html`
                ${this.listing.folders.map(
                  (folderName) => html`
                    <button
                      class="gc-browser-item"
                      @click=${() => void this.navigate(`${this.currentPath}/${folderName}`)}
                    >
                      <sp-icon-folder class="gc-browser-icon" size="s"></sp-icon-folder>
                      <span class="gc-browser-name">${folderName}</span>
                      <sp-icon-chevron-right class="gc-browser-chevron" size="xs"></sp-icon-chevron-right>
                    </button>
                  `,
                )}
                ${this.listing.files.map(
                  (existingName) => html`
                    <button
                      class="gc-browser-item"
                      title="Use this file name (overwrites on save)"
                      @click=${() => (this.fileName = existingName)}
                    >
                      <sp-icon-file-code class="gc-browser-icon" size="s"></sp-icon-file-code>
                      <span class="gc-browser-name">${existingName}</span>
                    </button>
                  `,
                )}
                ${!this.listing.folders.length && !this.listing.files.length
                  ? html`<p class="gc-muted">This folder is empty.</p>`
                  : nothing}
              `}
        </div>

        <sp-field-label for="gc-save-file-name">File Name</sp-field-label>
        <sp-textfield
          id="gc-save-file-name"
          placeholder="script.groovy"
          .value=${this.fileName}
          @input=${(event: Event) => (this.fileName = (event.target as HTMLInputElement).value)}
          @keydown=${(event: KeyboardEvent) => event.key === 'Enter' && this.confirm()}
        ></sp-textfield>
        ${targetPath
          ? html`<sp-help-text size="s">Saves to <code>${targetPath}</code></sp-help-text>`
          : nothing}
      </sp-dialog-wrapper>
    `;
  }
}
