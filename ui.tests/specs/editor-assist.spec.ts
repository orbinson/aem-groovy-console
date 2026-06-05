import { expect, test } from '@playwright/test';
import { openModernUi, setScriptContent } from './helpers';

test.describe('editor assistance', () => {
  test('suggests classes from the dictionary', async ({ page }) => {
    await openModernUi(page);
    await setScriptContent(page, '');

    const editor = page.locator('gc-script-editor .monaco-editor').first();
    await editor.click();
    await page.keyboard.type('def s = new Sessi', { delay: 80 });

    const suggestWidget = page.locator('.suggest-widget');
    await expect(suggestWidget).toBeVisible({ timeout: 15_000 });
    await expect(suggestWidget).toContainText('Session', { timeout: 15_000 });
  });

  test('suggests bindings', async ({ page }) => {
    await openModernUi(page);
    await setScriptContent(page, '');

    const editor = page.locator('gc-script-editor .monaco-editor').first();
    await editor.click();
    await page.keyboard.type('resourceRes', { delay: 80 });

    const suggestWidget = page.locator('.suggest-widget');
    await expect(suggestWidget).toBeVisible({ timeout: 15_000 });
    await expect(suggestWidget).toContainText('resourceResolver');
  });

  test('shows compile error squiggles for invalid scripts', async ({ page }) => {
    await openModernUi(page);

    await setScriptContent(page, 'def {invalid');

    // server-side compile is debounced (~500ms) before markers appear
    await expect(page.locator('gc-script-editor .squiggly-error').first()).toBeVisible({ timeout: 15_000 });
  });

  test('clears squiggles for valid scripts', async ({ page }) => {
    await openModernUi(page);

    await setScriptContent(page, 'def {invalid');
    await expect(page.locator('gc-script-editor .squiggly-error').first()).toBeVisible({ timeout: 15_000 });

    await setScriptContent(page, 'return 42');
    await expect(page.locator('gc-script-editor .squiggly-error')).toHaveCount(0, { timeout: 15_000 });
  });

  test('inserts an OSGi service lookup via the editor action', async ({ page }) => {
    await openModernUi(page);
    await setScriptContent(page, '');

    await page.evaluate(() => {
      const editor = (document.querySelector('gc-script-editor') as HTMLElement & {
        monacoEditor: { setPosition(p: { lineNumber: number; column: number }): void; trigger(s: string, h: string, p: unknown): void; getAction(id: string): { run(): Promise<void> } };
      }).monacoEditor;
      editor.setPosition({ lineNumber: 1, column: 1 });
      return editor.getAction('gc.insertService').run();
    });

    // the snippet is inserted and the service-name suggestions open inside the quotes
    await expect
      .poll(async () =>
        page.evaluate(() => (document.querySelector('gc-script-editor') as HTMLElement & { value: string }).value),
      )
      .toContain('def service = getService("")');
    await expect(page.locator('.suggest-widget')).toBeVisible({ timeout: 15_000 });
  });
});
