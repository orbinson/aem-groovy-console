import { css, html, LitElement } from 'lit';
import { customElement } from 'lit/decorators.js';
import { config } from '../config';

/**
 * AEM global-navigation strip shown above the app bar when running on AEM (config.aem), matching the
 * Coral shell header (48px, #1e1e1e) so the console reads as a regular Tools page. The logo links back
 * to the AEM start page. Hosts render it conditionally; on plain Sling it is never rendered.
 */
@customElement('gc-aem-nav')
export class GcAemNav extends LitElement {
  static styles = css`
    :host {
      display: block;
      flex: none;
      height: 48px;
      background: #1e1e1e;
    }

    a {
      display: inline-flex;
      align-items: center;
      gap: 12px;
      height: 100%;
      padding: 0 16px;
      color: #949494;
      font-size: 14px;
      text-decoration: none;
      transition: color 130ms ease-out;
    }

    a:hover,
    a:focus-visible {
      color: #fff;
    }

    a:focus-visible {
      outline: 2px solid #378ef0;
      outline-offset: -2px;
    }

    svg {
      width: 22px;
      height: 19px;
      flex: none;
    }
  `;

  protected render() {
    return html`
      <a href="${config.contextPath}/aem/start.html" title="Go to Adobe Experience Manager">
        <svg viewBox="0 0 30 26" aria-hidden="true">
          <path
            fill="#EB1000"
            d="M19.1 0H30v26L19.1 0zM10.9 0H0v26L10.9 0zM15 9.6 21.9 26h-4.5l-2.1-5.2h-5.1L15 9.6z"
          />
        </svg>
        Adobe Experience Manager
      </a>
    `;
  }
}
