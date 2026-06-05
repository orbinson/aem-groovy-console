import { html, LitElement, nothing } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import type { AssistContextResponse } from '../api/types';
import { config } from '../config';
import { getAssistContext } from '../editor/assist-data';
import { ENHANCEMENTS_DOC_URL, METHODS, NODE_BUILDER_EXAMPLE, PAGE_BUILDER_EXAMPLE } from '../data/reference-content';

/** Reference panels: bindings, imports, methods, builders, enhancements, and about. */
@customElement('gc-reference')
export class GcReference extends LitElement {
  @state() private context: AssistContextResponse | null = null;

  createRenderRoot(): this {
    return this;
  }

  connectedCallback(): void {
    super.connectedCallback();
    void getAssistContext()
      .then((context) => (this.context = context))
      .catch(() => undefined);
  }

  protected render() {
    return html`
      <details class="gc-section">
        <summary>Bindings</summary>
        <p class="gc-muted">The variables listed below are bound to each script.</p>
        ${this.context
          ? html`
              <ul class="gc-reference-list">
                ${this.context.bindings.map(
                  (binding) => html`
                    <li>
                      <code>${binding.name}</code> —
                      ${binding.link
                        ? html`<a href=${binding.link} target="_blank" rel="noopener">${binding.type}</a>`
                        : html`${binding.type}`}
                    </li>
                  `,
                )}
              </ul>
            `
          : nothing}
      </details>

      <details class="gc-section">
        <summary>Imports</summary>
        <p class="gc-muted">The packages listed below are imported into each script (star imports).</p>
        ${this.context
          ? html`
              <ul class="gc-reference-list">
                ${this.context.starImports.map(
                  (starImport) => html`
                    <li>
                      ${starImport.link
                        ? html`
                            <a href=${starImport.link} target="_blank" rel="noopener">
                              <code>${starImport.packageName}.*</code>
                            </a>
                          `
                        : html`<code>${starImport.packageName}.*</code>`}
                    </li>
                  `,
                )}
              </ul>
            `
          : nothing}
      </details>

      <details class="gc-section">
        <summary>Methods</summary>
        <p class="gc-muted">The methods listed below are available for use in all scripts.</p>
        <ul class="gc-reference-list">
          ${METHODS.map((method) => html`<li><code>${method.signature}</code> — ${method.description}</li>`)}
        </ul>
      </details>

      <details class="gc-section">
        <summary>Builders</summary>
        <p class="gc-muted">
          Additional binding variables are provided for the following
          <a href="https://groovy-lang.org/dsls.html#_builders" target="_blank" rel="noopener">builders</a>. Builders
          use a special syntax to create a structured tree of content in the JCR.
        </p>
        <h5>nodeBuilder</h5>
        <p class="gc-muted">
          Each "node" in the syntax tree corresponds to a Node in the repository. A new Node is created only if there
          is no existing node for the current name.
        </p>
        <pre class="gc-code">${NODE_BUILDER_EXAMPLE}</pre>
        <h5>pageBuilder</h5>
        <p class="gc-muted">
          Each "node" in the syntax tree corresponds to a cq:Page node, unless the node is a descendant of a
          "jcr:content" node, in which case nodes are treated as for the Node builder.
        </p>
        <pre class="gc-code">${PAGE_BUILDER_EXAMPLE}</pre>
      </details>

      <details class="gc-section">
        <summary>Enhancements</summary>
        <p class="gc-muted">
          See the AEM Groovy Extension documentation
          <a href=${ENHANCEMENTS_DOC_URL} target="_blank" rel="noopener">here</a> for details on registered
          metaclasses.
        </p>
        ${this.context?.metaClasses.length
          ? html`
              <ul class="gc-reference-list">
                ${this.context.metaClasses.map((metaClass) => html`<li><code>${metaClass.type}</code></li>`)}
              </ul>
            `
          : nothing}
      </details>

      <details class="gc-section">
        <summary>About</summary>
        <ul class="gc-reference-list">
          <li>
            Inspired by Guillaume Laforge's
            <a href="https://groovyconsole.appspot.com" target="_blank" rel="noopener">Groovy Web Console</a>.
          </li>
          <li>
            Implemented with
            <a href="https://www.groovy-lang.org" target="_blank" rel="noopener">Groovy</a>
            ${config.groovyVersion ? `version ${config.groovyVersion}` : ''} and
            <a href="https://opensource.adobe.com/spectrum-web-components/" target="_blank" rel="noopener">
              Spectrum Web Components</a>.
          </li>
          <li>
            Code editing capabilities provided by
            <a href="https://microsoft.github.io/monaco-editor/" target="_blank" rel="noopener">Monaco Editor</a>.
          </li>
          <li>
            Project hosted on
            <a href="https://github.com/orbinson/aem-groovy-console" target="_blank" rel="noopener">GitHub</a>.
          </li>
        </ul>
      </details>

      <p class="gc-muted">
        Prefer the previous interface?
        <a class="gc-classic-link" href=${config.classicUrl}>Switch to the Classic UI</a>
      </p>
    `;
  }
}
