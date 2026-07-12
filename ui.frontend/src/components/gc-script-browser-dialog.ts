import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { listScriptsFolder, SCRIPTS_FOLDER, type FolderListing } from '../api/console-api';

/**
 * Folder-navigable script browser for opening saved scripts: breadcrumb + folder/file list
 * backed by the Sling default GET servlet. Works on both AEM and plain Sling.
 */
@customElement('gc-script-browser-dialog')
export class GcScriptBrowserDialog extends LitElement {
  @state() private open = false;
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

  async show(): Promise<void> {
    this.open = true;
    await this.navigate(SCRIPTS_FOLDER);
  }

  private async navigate(path: string): Promise<void> {
    this.currentPath = path;
    this.loading = true;

    try {
      this.listing = await listScriptsFolder(path);
    } catch {
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

  private select(fileName: string): void {
    const path = `${this.currentPath}/${fileName}`;

    this.open = false;
    this.dispatchEvent(new CustomEvent('gc-open-script', { detail: { path }, bubbles: true, composed: true }));
  }

  protected render() {
    if (!this.open) {
      return nothing;
    }

    const crumbs = this.breadcrumbs;
    const empty = !this.loading && !this.listing.folders.length && !this.listing.files.length;

    return html`
      <sp-dialog-wrapper
        open
        underlay
        dismissable
        headline="Open Script"
        cancel-label="Cancel"
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

        <div class="gc-browser-list">
          ${this.loading
            ? html`<sp-progress-circle indeterminate size="m" aria-label="Loading scripts"></sp-progress-circle>`
            : html`
                ${this.listing.folders.map(
                  (name) => html`
                    <button class="gc-browser-item" @click=${() => void this.navigate(`${this.currentPath}/${name}`)}>
                      <sp-icon-folder class="gc-browser-icon" size="s"></sp-icon-folder>
                      <span class="gc-browser-name">${name}</span>
                      <sp-icon-chevron-right class="gc-browser-chevron" size="xs"></sp-icon-chevron-right>
                    </button>
                  `,
                )}
                ${this.listing.files.map(
                  (name) => html`
                    <button class="gc-browser-item" @click=${() => this.select(name)}>
                      <sp-icon-file-code class="gc-browser-icon" size="s"></sp-icon-file-code>
                      <span class="gc-browser-name">${name}</span>
                    </button>
                  `,
                )}
                ${empty ? html`<p class="gc-muted">This folder is empty.</p>` : nothing}
              `}
        </div>
      </sp-dialog-wrapper>
    `;
  }
}
