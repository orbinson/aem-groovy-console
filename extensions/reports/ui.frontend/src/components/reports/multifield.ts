import { html } from 'lit';

/** Options for {@link renderMultifield}. */
export interface MultifieldOptions {
  /** Current values; at least one (possibly empty) row is always rendered. */
  entries: string[];
  /** Render the input control for the entry at `index` (the caller owns the control type and change handling). */
  renderEntry: (entry: string, index: number) => unknown;
  /** Append a new empty entry. */
  onAdd: () => void;
  /** Remove the entry at `index`. */
  onRemove: (index: number) => void;
  /** Label for the add button (defaults to "Add value"). */
  addLabel?: string;
}

/**
 * Render a repeatable multifield — one row per value with a remove (x) button, plus an "Add" button — shared by
 * the run form and the editor's try-out. The caller supplies `renderEntry` so each context can render its own
 * control (a typed input, a path/tag picker, …); this helper only owns the add/remove chrome and layout.
 */
export function renderMultifield(options: MultifieldOptions) {
  const entries = options.entries.length ? options.entries : [''];

  return html`
    <div class="gcr-multifield">
      ${entries.map(
        (entry, index) => html`
          <div class="gcr-multifield-row">
            ${options.renderEntry(entry, index)}
            <sp-action-button
              quiet
              size="s"
              class="gcr-multifield-remove"
              label="Remove value"
              title="Remove value"
              ?disabled=${entries.length === 1 && !entry}
              @click=${() => options.onRemove(index)}
            >
              <sp-icon-close slot="icon"></sp-icon-close>
            </sp-action-button>
          </div>
        `,
      )}
    </div>
    <sp-action-button quiet size="s" class="gcr-multifield-add" @click=${() => options.onAdd()}>
      <sp-icon-add slot="icon"></sp-icon-add>
      ${options.addLabel ?? 'Add value'}
    </sp-action-button>
  `;
}
