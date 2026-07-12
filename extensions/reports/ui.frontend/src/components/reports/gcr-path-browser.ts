import { html, LitElement, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { browsePath } from '../../api/reports-api';
import type { BrowseNode, PathType } from '../../api/reports-types';

const DEFAULT_ROOT: Record<PathType, string> = {
  NODE: '/',
  PAGE: '/content',
  ASSET: '/content/dam',
};

const TITLE: Record<PathType, string> = {
  NODE: 'Select a path',
  PAGE: 'Select a page',
  ASSET: 'Select an asset',
};

/**
 * AEM Granite-style path browser modal: a lazily-loaded JCR tree, scoped by pathType (any node, AEM page or
 * DAM asset).  Opened via openBrowser(); emits `gcr-path-selected` { path } on confirm.  Self-contained and
 * rendered in light DOM so the reports stylesheet applies.
 */
@customElement('gcr-path-browser')
export class GcrPathBrowser extends LitElement {
  @property() pathType: PathType = 'NODE';
  @property() rootPath = '';

  @state() private open = false;
  @state() private root = '/';
  @state() private selected = '';
  @state() private focused = '';
  @state() private expanded = new Set<string>();
  @state() private loading = new Set<string>();
  @state() private childrenByPath: Record<string, BrowseNode[]> = {};
  @state() private truncatedByPath: Record<string, boolean> = {};

  /** path of the tree row to focus after the next render (roving tabindex) */
  private pendingFocus: string | null = null;
  /** focus the dialog container after render when there is no row to focus (empty tree) */
  private pendingFocusDialog = false;
  /** element to restore focus to when the modal closes */
  private previousActiveElement: HTMLElement | null = null;

  createRenderRoot(): this {
    return this;
  }

  protected updated(): void {
    if (this.pendingFocus !== null) {
      const target = this.pendingFocus;
      this.pendingFocus = null;
      for (const row of this.querySelectorAll<HTMLElement>('.gcr-browser-row')) {
        if (row.dataset.path === target) {
          row.focus();
          return;
        }
      }
    }
    if (this.pendingFocusDialog) {
      this.pendingFocusDialog = false;
      // empty tree (or nothing to focus) — move focus into the dialog so it isn't left behind the modal
      this.querySelector<HTMLElement>('.gcr-browser')?.focus();
    }
  }

  /** Keep Tab focus inside the modal (cycles Close → tree row → Cancel → Select → wrap). */
  private trapFocus(event: KeyboardEvent): void {
    const focusables = [
      ...this.querySelectorAll<HTMLElement>(
        '.gcr-browser sp-action-button, .gcr-browser .gcr-browser-row[tabindex="0"], .gcr-browser sp-button',
      ),
    ].filter((element) => !element.hasAttribute('disabled') && element.offsetParent !== null);

    if (!focusables.length) {
      return;
    }

    event.preventDefault();
    const index = focusables.indexOf(document.activeElement as HTMLElement);
    if (index === -1) {
      focusables[0].focus();
      return;
    }
    const next = event.shiftKey
      ? (index - 1 + focusables.length) % focusables.length
      : (index + 1) % focusables.length;
    focusables[next].focus();
  }

  /** Open the browser, rooted at the configured rootPath (or the type default), revealing the current value. */
  async openBrowser(currentValue: string): Promise<void> {
    this.previousActiveElement = (document.activeElement as HTMLElement) ?? null;
    this.root = (this.rootPath || DEFAULT_ROOT[this.pathType] || '/').replace(/\/$/, '') || '/';
    this.selected = currentValue || '';
    this.focused = '';
    this.expanded = new Set();
    this.childrenByPath = {};
    this.truncatedByPath = {};
    this.open = true;

    await this.loadChildren(this.root);
    await this.revealPath(currentValue);

    // focus the selected row if visible, otherwise the first row; if the tree is empty, focus the dialog
    const visible = this.visibleNodes();
    const focusTarget = visible.find((node) => node.path === this.selected) ?? visible[0];
    if (focusTarget) {
      this.focused = focusTarget.path;
      this.pendingFocus = focusTarget.path;
    } else {
      this.pendingFocusDialog = true;
    }
  }

  private close(): void {
    this.open = false;
    this.previousActiveElement?.focus();
  }

  private confirm(): void {
    if (!this.selected) {
      return;
    }
    this.dispatchEvent(
      new CustomEvent('gcr-path-selected', { detail: { path: this.selected }, bubbles: true, composed: true }),
    );
    this.open = false;
    this.previousActiveElement?.focus();
  }

  private async loadChildren(path: string): Promise<void> {
    if (this.childrenByPath[path]) {
      return;
    }
    this.loading = new Set(this.loading).add(path);
    try {
      const response = await browsePath(path, this.pathType);
      this.childrenByPath = { ...this.childrenByPath, [path]: response.children };
      this.truncatedByPath = { ...this.truncatedByPath, [path]: Boolean(response.truncated) };
    } catch {
      this.childrenByPath = { ...this.childrenByPath, [path]: [] };
    } finally {
      const loading = new Set(this.loading);
      loading.delete(path);
      this.loading = loading;
    }
  }

  /** Expand each ancestor of the current value so the selection is visible on open. */
  private async revealPath(value: string): Promise<void> {
    const root = this.root;
    // require a path boundary so root "/content" does not match "/content-archive/..."
    const underRoot = root === '/' ? value.startsWith('/') : value === root || value.startsWith(`${root}/`);
    if (!value || !underRoot) {
      return;
    }

    const remainder = root === '/' ? value.slice(1) : value.slice(root.length + 1);
    if (!remainder) {
      return;
    }

    let current = this.root;
    for (const segment of remainder.split('/')) {
      current = current === '/' ? `/${segment}` : `${current}/${segment}`;
      if (current === value) {
        break; // don't expand the leaf itself
      }
      this.expanded = new Set(this.expanded).add(current);
      await this.loadChildren(current);
    }
  }

  private async toggle(node: BrowseNode): Promise<void> {
    const expanded = new Set(this.expanded);
    if (expanded.has(node.path)) {
      expanded.delete(node.path);
      this.expanded = expanded;
      return;
    }
    expanded.add(node.path);
    this.expanded = expanded;
    await this.loadChildren(node.path);
  }

  private onRowClick(node: BrowseNode): void {
    this.focused = node.path;
    if (node.selectable) {
      this.selected = node.path;
    }
    // clicking the row also expands/collapses, so you don't have to aim for the small twisty
    if (node.hasChildren) {
      void this.toggle(node);
    }
  }

  /** Double click selects and confirms in one gesture, like the Granite path pickers. */
  private onRowDblClick(node: BrowseNode): void {
    if (node.selectable) {
      this.selected = node.path;
      this.confirm();
    }
  }

  /** Nodes currently visible in the tree, in display order (root children + expanded descendants). */
  private visibleNodes(): BrowseNode[] {
    const out: BrowseNode[] = [];
    const walk = (path: string): void => {
      for (const node of this.childrenByPath[path] ?? []) {
        out.push(node);
        if (this.expanded.has(node.path)) {
          walk(node.path);
        }
      }
    };
    walk(this.root);
    return out;
  }

  private focusNode(node: BrowseNode | undefined): void {
    if (node) {
      this.focused = node.path;
      this.pendingFocus = node.path;
    }
  }

  /** Keyboard navigation for the tree (roving tabindex), per WAI-ARIA tree pattern. */
  private onTreeKeydown(event: KeyboardEvent): void {
    const visible = this.visibleNodes();
    if (!visible.length) {
      return;
    }

    const index = visible.findIndex((node) => node.path === this.focused);
    const node = index >= 0 ? visible[index] : null;

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.focusNode(visible[Math.min(index + 1, visible.length - 1)] ?? visible[0]);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.focusNode(index <= 0 ? visible[0] : visible[index - 1]);
        break;
      case 'ArrowRight':
        event.preventDefault();
        if (node?.hasChildren) {
          if (this.expanded.has(node.path)) {
            this.focusNode(visible[index + 1]);
          } else {
            void this.toggle(node);
          }
        }
        break;
      case 'ArrowLeft':
        event.preventDefault();
        if (node && this.expanded.has(node.path)) {
          void this.toggle(node);
        } else if (node) {
          const parentPath = node.path.slice(0, node.path.lastIndexOf('/'));
          this.focusNode(visible.find((candidate) => candidate.path === parentPath));
        }
        break;
      case 'Home':
        event.preventDefault();
        this.focusNode(visible[0]);
        break;
      case 'End':
        event.preventDefault();
        this.focusNode(visible[visible.length - 1]);
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        if (node?.selectable) {
          this.selected = node.path;
        } else if (node?.hasChildren) {
          void this.toggle(node);
        }
        break;
      default:
        break;
    }
  }

  protected render() {
    if (!this.open) {
      return nothing;
    }

    return html`
      <div class="gcr-browser-overlay" @click=${(event: Event) => event.target === event.currentTarget && this.close()}>
        <div
          class="gcr-browser"
          role="dialog"
          aria-modal="true"
          aria-label=${TITLE[this.pathType]}
          tabindex="-1"
          @keydown=${(event: KeyboardEvent) => {
            if (event.key === 'Escape') {
              event.stopPropagation();
              this.close();
            } else if (event.key === 'Tab') {
              this.trapFocus(event);
            }
          }}
        >
          <header class="gcr-browser-header">
            <h2>${TITLE[this.pathType]}</h2>
            <sp-action-button quiet label="Close" aria-label="Close" @click=${() => this.close()}>
              <sp-icon-close slot="icon"></sp-icon-close>
            </sp-action-button>
          </header>

          <div
            class="gcr-browser-tree"
            role="tree"
            aria-label=${TITLE[this.pathType]}
            @keydown=${(event: KeyboardEvent) => this.onTreeKeydown(event)}
          >
            ${this.renderChildren(this.root, 0)}
          </div>

          <footer class="gcr-browser-footer">
            <span class="gcr-browser-selected">${this.selected || 'No selection'}</span>
            <div class="gcr-browser-actions">
              <sp-button variant="secondary" @click=${() => this.close()}>Cancel</sp-button>
              <sp-button variant="accent" ?disabled=${!this.selected} @click=${() => this.confirm()}>Select</sp-button>
            </div>
          </footer>
        </div>
      </div>
    `;
  }

  private renderChildren(path: string, depth: number): unknown {
    if (this.loading.has(path) && !this.childrenByPath[path]) {
      return html`<div class="gcr-browser-loading" style=${`padding-left:${depth * 16 + 12}px`}>
        <sp-progress-circle indeterminate size="s"></sp-progress-circle>
      </div>`;
    }

    const children = this.childrenByPath[path] ?? [];
    if (children.length === 0) {
      return depth === 0
        ? html`<div class="gcr-browser-empty">Nothing to show here.</div>`
        : nothing;
    }

    const rows: unknown[] = children.map((node) => {
      const isExpanded = this.expanded.has(node.path);
      return html`
        <div
          class="gcr-browser-row ${this.selected === node.path ? 'is-selected' : ''} ${node.selectable
            ? 'is-selectable'
            : ''}"
          style=${`padding-left:${depth * 16 + 8}px`}
          role="treeitem"
          data-path=${node.path}
          tabindex=${this.focused === node.path ? '0' : '-1'}
          aria-level=${depth + 1}
          aria-selected=${this.selected === node.path}
          aria-expanded=${node.hasChildren ? String(isExpanded) : nothing}
          @click=${() => this.onRowClick(node)}
          @dblclick=${() => this.onRowDblClick(node)}
        >
          <span
            class="gcr-browser-twisty ${node.hasChildren ? '' : 'is-leaf'} ${isExpanded ? 'is-expanded' : ''}"
            aria-hidden="true"
            @click=${(event: Event) => {
              event.stopPropagation();
              if (node.hasChildren) {
                void this.toggle(node);
              }
            }}
            >${node.hasChildren ? html`<sp-icon-chevron-right size="xs"></sp-icon-chevron-right>` : ''}</span
          >
          <span class="gcr-browser-name">${node.title || node.name}</span>
          ${node.title ? html`<span class="gcr-browser-subtle">${node.name}</span>` : nothing}
        </div>
        ${isExpanded ? html`<div role="group">${this.renderChildren(node.path, depth + 1)}</div>` : nothing}
      `;
    });

    if (this.truncatedByPath[path]) {
      rows.push(
        html`<div class="gcr-browser-truncated" style=${`padding-left:${depth * 16 + 24}px`}>
          More items not shown — refine the path in the text field.
        </div>`,
      );
    }

    return rows;
  }
}
