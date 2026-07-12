import { css, html, LitElement, nothing } from 'lit';
import { customElement, property } from 'lit/decorators.js';

/** Slide-over drawer chrome anchored next to the activity rail; content is slotted lazily by gc-app. */
@customElement('gc-drawer')
export class GcDrawer extends LitElement {
  static styles = css`
    :host {
      display: contents;
    }

    /* Overlay the workspace only: the app bar and the activity rail stay visible and
       interactive, so panels can be switched directly from the rail. */
    .backdrop {
      position: fixed;
      top: var(--gc-drawer-top, 49px);
      bottom: 0;
      left: 48px;
      right: 0;
      background: rgba(0, 0, 0, 0.35);
      z-index: 200;
      animation: gc-drawer-fade 130ms ease-out;
    }

    .panel {
      position: fixed;
      top: var(--gc-drawer-top, 49px);
      bottom: 0;
      left: 48px;
      width: min(960px, calc(100vw - 48px));
      display: flex;
      flex-direction: column;
      background: var(--spectrum-gray-50);
      color: var(--spectrum-gray-800);
      border-right: 1px solid var(--spectrum-gray-300);
      box-shadow: 8px 0 24px rgba(0, 0, 0, 0.12);
      z-index: 201;
      animation: gc-drawer-slide 160ms ease-out;
    }

    @keyframes gc-drawer-slide {
      from {
        transform: translateX(-24px);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }

    @keyframes gc-drawer-fade {
      from {
        opacity: 0;
      }
      to {
        opacity: 1;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .panel,
      .backdrop {
        animation: none;
      }
    }

    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 16px;
      border-bottom: 1px solid var(--spectrum-gray-300);
    }

    .header h3 {
      margin: 0;
      font-size: 15px;
    }

    .body {
      flex: 1;
      overflow-y: auto;
      overflow-x: auto;
      padding: 12px 16px;
    }
  `;

  @property({ type: Boolean, reflect: true }) open = false;
  @property() heading = '';

  private keyListener = (event: KeyboardEvent): void => {
    if (event.key === 'Escape' && this.open) {
      this.close();
    }
  };

  connectedCallback(): void {
    super.connectedCallback();
    window.addEventListener('keydown', this.keyListener);
  }

  disconnectedCallback(): void {
    window.removeEventListener('keydown', this.keyListener);
    super.disconnectedCallback();
  }

  private close(): void {
    this.dispatchEvent(new CustomEvent('gc-drawer-close', { bubbles: true, composed: true }));
  }

  protected render() {
    if (!this.open) {
      return nothing;
    }

    return html`
      <div class="backdrop" @click=${this.close}></div>
      <aside class="panel" role="dialog" aria-label=${this.heading}>
        <div class="header">
          <h3>${this.heading}</h3>
          <sp-action-button size="s" quiet @click=${this.close} aria-label="Close">
            <sp-icon-close slot="icon"></sp-icon-close>
          </sp-action-button>
        </div>
        <div class="body">
          <slot></slot>
        </div>
      </aside>
    `;
  }
}
