import { html } from 'lit';
import type { ResultCell, ResultColumn, ResultLinkCell } from '../../api/reports-types';

/** Allow only http/https/mailto and relative URLs; blocks javascript:/data: and other dangerous schemes. */
export function isSafeHref(href: string | undefined): boolean {
  if (!href) {
    return false;
  }
  try {
    const url = new URL(href, window.location.origin);
    return ['http:', 'https:', 'mailto:'].includes(url.protocol);
  } catch {
    return false;
  }
}

export function formatDate(iso?: string | null): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString();
}

/** Render a single result cell by its column type. */
export function renderCell(cell: ResultCell, type: string) {
  if (cell === null || cell === undefined) {
    return html`<span class="gcr-cell-empty">—</span>`;
  }

  switch (type) {
    case 'LINK': {
      const link = (typeof cell === 'object' ? cell : { text: String(cell), href: String(cell) }) as ResultLinkCell;
      const label = link.text || link.href;
      // only render an anchor for safe schemes — report data must not yield a javascript:/data: link
      return isSafeHref(link.href)
        ? html`<a href=${link.href} target="_blank" rel="noopener">${label}</a>`
        : html`${label}`;
    }
    case 'BOOLEAN':
      return cell === true || cell === 'true' ? '✓' : '—';
    case 'DATE':
      return formatDate(String(cell));
    case 'NUMBER': {
      // format in the user's locale (grouping + decimal separator) for display
      const value = typeof cell === 'number' ? cell : Number(cell);
      return Number.isNaN(value) ? String(cell) : value.toLocaleString();
    }
    default:
      return String(cell);
  }
}

/** Render the typed result table (no pagination). Shared by the run view and the editor preview. */
export function renderResultTable(columns: ResultColumn[], rows: ResultCell[][]) {
  return html`
    <div class="gcr-table-wrapper">
      <table class="gcr-table">
        <caption class="gcr-visually-hidden">Report result</caption>
        <thead>
          <tr>
            ${columns.map((column) => html`<th scope="col" class="gcr-cell-${column.type.toLowerCase()}">${column.name}</th>`)}
          </tr>
        </thead>
        <tbody>
          ${rows.map(
            (row) => html`
              <tr>
                ${row.map(
                  (cell, index) => html`
                    <td class="gcr-cell-${(columns[index]?.type ?? 'STRING').toLowerCase()}">
                      ${renderCell(cell, columns[index]?.type ?? 'STRING')}
                    </td>
                  `,
                )}
              </tr>
            `,
          )}
        </tbody>
      </table>
    </div>
  `;
}
