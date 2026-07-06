/*
 * Query-audit panel for the modern AEM Groovy Console.
 *
 * Self-contained ES module (no build step, no framework): defines the <query-audit-panel> custom element and
 * registers it in the console's activity rail. The panel POSTs a script to /bin/groovyconsole/query-audit and shows,
 * per JCR query the script runs, whether the live Oak instance has an index that covers it.
 */
(() => {
  const ENDPOINT = '/bin/groovyconsole/query-audit';

  const ICON =
    '<svg viewBox="0 0 18 18" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.5">' +
    '<path d="M2 4h10M2 8h7M2 12h5"/><circle cx="13" cy="12" r="3"/><path d="M15.2 14.2L17 16"/></svg>';

  class QueryAuditPanel extends HTMLElement {
    connectedCallback() {
      if (this.dataset.ready) {
        return;
      }
      this.dataset.ready = 'true';
      this.innerHTML = `
        <style>
          .qa { display: flex; flex-direction: column; gap: 12px; padding: 12px; font: 13px/1.4 system-ui, sans-serif; }
          .qa button { align-self: flex-start; padding: 6px 14px; cursor: pointer; }
          .qa table { width: 100%; border-collapse: collapse; }
          .qa th, .qa td { text-align: left; padding: 6px 8px; border-bottom: 1px solid rgba(128,128,128,.3); vertical-align: top; }
          .qa td.stmt { font-family: monospace; white-space: pre-wrap; word-break: break-word; }
          .qa .needs { color: #d7373f; font-weight: 600; }
          .qa .ok { color: #268e6c; font-weight: 600; }
          .qa .msg { opacity: .8; }
        </style>
        <div class="qa">
          <button type="button">Audit active script</button>
          <div class="results msg">Runs the script currently in the editor and reports, per JCR query, whether an Oak index covers it.</div>
        </div>`;

      this._results = this.querySelector('.results');
      this.querySelector('button').addEventListener('click', () => this._audit());
    }

    async _audit() {
      const script = (window.GroovyConsole?.getScript?.() ?? '').trim();
      if (!script) {
        this._results.className = 'results msg';
        this._results.textContent = 'The editor is empty — nothing to audit.';
        return;
      }

      this._results.className = 'results msg';
      this._results.textContent = 'Auditing…';

      try {
        const response = await fetch(ENDPOINT, {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: 'script=' + encodeURIComponent(script),
        });
        if (!response.ok) {
          throw new Error('HTTP ' + response.status);
        }
        this._render(await response.json());
      } catch (e) {
        this._results.className = 'results msg';
        this._results.textContent = 'Audit failed: ' + e.message;
      }
    }

    _render(report) {
      if (report.exceptionStackTrace) {
        this._results.className = 'results msg';
        this._results.textContent = 'Script failed:\n' + report.exceptionStackTrace;
        return;
      }

      const queries = report.queries || [];
      if (!queries.length) {
        this._results.className = 'results msg';
        this._results.textContent = 'The script executed no JCR queries.';
        return;
      }

      this._results.className = 'results';
      const rows = queries
        .map(
          (q) =>
            `<tr><td class="${q.needsIndex ? 'needs' : 'ok'}">${q.needsIndex ? 'needs index' : 'indexed'}</td>` +
            `<td class="stmt">${escapeHtml(q.statement)}</td>` +
            `<td class="stmt">${escapeHtml(q.plan)}</td></tr>`
        )
        .join('');
      this._results.innerHTML =
        '<table><thead><tr><th>Index</th><th>Query</th><th>Oak plan</th></tr></thead><tbody>' +
        rows +
        '</tbody></table>';
    }
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;');
  }

  if (!customElements.get('query-audit-panel')) {
    customElements.define('query-audit-panel', QueryAuditPanel);
  }

  if (window.GroovyConsole && typeof window.GroovyConsole.registerPanel === 'function') {
    window.GroovyConsole.registerPanel({
      id: 'query-audit',
      title: 'Query audit',
      element: 'query-audit-panel',
      iconHtml: ICON,
    });
  }
})();
