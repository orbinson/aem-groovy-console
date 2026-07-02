// Spectrum's sp-textfield renders its placeholder in the same colour as typed text, so empty
// fields look like they already hold a value. The placeholder lives in shadow DOM with no exposed
// part or colour token, so we adopt a small stylesheet into each textfield's shadow root to mute it.

const placeholderSheet = new CSSStyleSheet();
placeholderSheet.replaceSync(
  '.input::placeholder{color:var(--spectrum-gray-500);font-style:italic;opacity:1;}',
);

// fields whose shadow root we've already styled — avoids re-scheduling updateComplete work every render
const processed = new WeakSet<Element>();

/** Mute the placeholder colour of every sp-textfield within the given light-DOM host. */
export function mutePlaceholders(host: ParentNode): void {
  host.querySelectorAll('sp-textfield').forEach((field) => {
    if (processed.has(field)) {
      return;
    }
    processed.add(field);

    const element = field as HTMLElement & { updateComplete?: Promise<unknown> };

    const apply = (): void => {
      const root = element.shadowRoot;
      if (root && !root.adoptedStyleSheets.includes(placeholderSheet)) {
        root.adoptedStyleSheets = [...root.adoptedStyleSheets, placeholderSheet];
      }
    };

    if (element.updateComplete) {
      void element.updateComplete.then(apply);
    } else {
      apply();
    }
  });
}
