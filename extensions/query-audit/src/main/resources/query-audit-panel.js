/*
 * Query-audit integration for the modern AEM Groovy Console.
 *
 * Self-contained ES module (no build step, no framework). Registers with the console shell:
 *
 *  - a run action "Run with query audit" in the Run button's options menu — POSTs the active script to
 *    /bin/groovyconsole/query-audit, which executes it while capturing every JCR query it runs
 *  - a "Query audit" result tab in the output dock (next to Log/Result) rendered by the
 *    <query-audit-result> custom element, showing per query whether an Oak index covers it
 */
(() => {
  const ENDPOINT = '/bin/groovyconsole/query-audit';

  class QueryAuditResult extends HTMLElement {
    get result() {
      return this._result;
    }

    set result(value) {
      if (value === this._result) {
        return;
      }
      this._result = value;
      this._render(value?.queryAudit);
    }

    _render(queries) {
      if (!Array.isArray(queries)) {
        this.innerHTML = '';
        return;
      }

      const needing = queries.filter((query) => query.needsIndex).length;

      let summary;
      let summaryClass = '';
      if (!queries.length) {
        summary = 'The script executed no JCR queries.';
      } else if (needing) {
        summary = `${queries.length} ${plural(queries.length, 'query', 'queries')} — ${needing} not covered by an Oak index`;
        summaryClass = 'qa-warn';
      } else {
        summary = `All ${queries.length} ${plural(queries.length, 'query is', 'queries are')} covered by an Oak index`;
        summaryClass = 'qa-good';
      }

      const rows = queries
        .map(
          (query) => `
            <div class="qa-row">
              <span class="qa-badge ${query.needsIndex ? 'qa-needs' : 'qa-ok'}">
                ${query.needsIndex ? 'needs index' : 'indexed'}
              </span>
              <div class="qa-detail">
                <code class="qa-statement">${escapeHtml(query.statement)}</code>
                <div class="qa-meta">
                  ${query.language ? `<span class="qa-lang">${escapeHtml(displayLanguage(query.language))}</span>` : ''}
                  <details class="qa-plan">
                    <summary>Oak plan</summary>
                    <pre>${highlightTraverse(escapeHtml(query.plan))}</pre>
                  </details>
                </div>
              </div>
            </div>`
        )
        .join('');

      this.innerHTML = `
        <style>
          /* match the dock's built-in tabs: .gc-dock-pre pads 10px 14px inside the unpadded dock body */
          query-audit-result { display: block; padding: 10px 14px; font-size: 13px; }
          query-audit-result .qa-summary { margin: 0 0 8px; font-weight: 600; }
          query-audit-result .qa-summary.qa-warn { color: var(--spectrum-negative-content-color-default, #d7373f); }
          query-audit-result .qa-summary.qa-good { color: var(--spectrum-positive-content-color-default, #268e6c); }
          query-audit-result .qa-row {
            display: flex; align-items: flex-start; gap: 10px;
            padding: 8px 0; border-top: 1px solid var(--spectrum-gray-200, rgba(128, 128, 128, 0.25));
          }
          query-audit-result .qa-badge {
            flex: none; box-sizing: border-box; min-width: 92px; margin-top: 1px; padding: 2px 8px;
            border-radius: 10px; text-align: center;
            font-size: 11px; font-weight: 700; white-space: nowrap; color: #fff;
          }
          query-audit-result .qa-needs { background: var(--spectrum-negative-background-color-default, #d7373f); }
          query-audit-result .qa-ok { background: var(--spectrum-positive-background-color-default, #268e6c); }
          query-audit-result .qa-detail { min-width: 0; flex: 1; }
          query-audit-result .qa-statement {
            display: block; font-family: var(--spectrum-code-font-family-stack, ui-monospace, Menlo, monospace);
            font-size: 12px; white-space: pre-wrap; word-break: break-word;
          }
          query-audit-result .qa-meta { display: flex; align-items: baseline; gap: 10px; margin-top: 4px; }
          query-audit-result .qa-lang {
            flex: none; padding: 1px 6px; border-radius: 4px;
            border: 1px solid var(--spectrum-gray-300, rgba(128, 128, 128, 0.4));
            font-size: 10px; font-weight: 600; letter-spacing: 0.3px; opacity: 0.75;
          }
          query-audit-result .qa-plan { flex: 1; min-width: 0; }
          query-audit-result .qa-plan summary { cursor: pointer; font-size: 12px; opacity: 0.75; }
          query-audit-result .qa-plan pre {
            margin: 4px 0 0; padding: 6px 10px; border-radius: 0 4px 4px 0;
            border-left: 3px solid var(--spectrum-gray-300, rgba(128, 128, 128, 0.4));
            background: var(--spectrum-gray-100, rgba(128, 128, 128, 0.12));
            font-family: var(--spectrum-code-font-family-stack, ui-monospace, Menlo, monospace);
            font-size: 12px; white-space: pre-wrap; word-break: break-word;
          }
          query-audit-result .qa-plan mark {
            background: transparent; font-weight: 700;
            color: var(--spectrum-negative-content-color-default, #d7373f);
          }
        </style>
        <div class="qa-summary ${summaryClass}">${summary}</div>
        ${rows}`;
    }
  }

  function plural(count, singular, pluralForm) {
    return count === 1 ? singular : pluralForm;
  }

  /** The backend reports JCR language constants ("JCR-SQL2", "xpath"); prettify the lowercase ones. */
  function displayLanguage(language) {
    return language === 'xpath' ? 'XPath' : language;
  }

  /** Emphasize the traversal marker inside an (already escaped) Oak plan. */
  function highlightTraverse(escapedPlan) {
    return escapedPlan.replace(/traverse/gi, (match) => `<mark>${match}</mark>`);
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;');
  }

  /** Run the script through the query-audit endpoint; the report becomes the console's run result. */
  async function runWithQueryAudit({ script, data }) {
    const body = new URLSearchParams({ script, data: data ?? '' });
    const response = await fetch(ENDPOINT, { method: 'POST', body });
    if (!response.ok) {
      throw new Error('HTTP ' + response.status);
    }
    const report = await response.json();
    if (report.error) {
      throw new Error(report.error);
    }
    return {
      result: report.result,
      output: report.output,
      exceptionStackTrace: report.exceptionStackTrace,
      runningTime: report.runningTime,
      queryAudit: report.queries || [],
    };
  }

  if (!customElements.get('query-audit-result')) {
    customElements.define('query-audit-result', QueryAuditResult);
  }

  const console_ = window.GroovyConsole;
  if (typeof console_?.registerRunAction === 'function' && typeof console_?.registerRunResultTab === 'function') {
    console_.registerRunAction({
      id: 'query-audit',
      label: 'Run with query audit',
      run: runWithQueryAudit,
    });
    console_.registerRunResultTab({
      id: 'query-audit',
      label: 'Query audit',
      element: 'query-audit-result',
      isRelevant: (result) => Array.isArray(result?.queryAudit),
    });
  } else {
    console.warn('[query-audit] this console version does not support run actions — update the AEM Groovy Console.');
  }
})();
