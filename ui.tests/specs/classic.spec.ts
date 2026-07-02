import { expect, test, type Page } from '@playwright/test';
import { CLASSIC_URL } from './helpers';

/** Wait until the classic UI's Ace editor global is initialised; the container becomes visible before
 *  `window.scriptEditor` is assigned, so gate editor access on the global to avoid a startup race. */
async function waitForClassicEditor(page: Page): Promise<void> {
  await page.waitForFunction(
    () =>
      typeof (window as unknown as { scriptEditor?: { getSession?: unknown } }).scriptEditor?.getSession ===
      'function',
    undefined,
    { timeout: 30_000 },
  );
}

/** Set the Ace editor content in the classic UI. */
async function setClassicScript(page: Page, content: string): Promise<void> {
  await waitForClassicEditor(page);
  await page.evaluate((script) => {
    (window as unknown as { scriptEditor: { getSession(): { setValue(v: string): void } } })
      .scriptEditor.getSession().setValue(script);
  }, content);
}

async function getClassicScript(page: Page): Promise<string> {
  await waitForClassicEditor(page);
  return page.evaluate(() =>
    (window as unknown as { scriptEditor: { getSession(): { getValue(): string } } })
      .scriptEditor.getSession().getValue(),
  );
}

test.describe('classic UI script dialogs', () => {
  test('saves and opens a script through the bootstrap modals', async ({ page }) => {
    await page.goto(CLASSIC_URL);
    await expect(page.locator('#script-editor')).toBeVisible({ timeout: 30_000 });

    const name = `pw-classic-${Date.now()}`;
    await setClassicScript(page, `// classic save test ${name}`);

    // save through the modal
    await page.click('#save-script');
    await expect(page.locator('#save-script-modal')).toBeVisible();
    await page.fill('#save-script-file-name', name);
    await page.click('#save-script-confirm');

    await expect(page.locator('#message-success')).toContainText('Script saved successfully.', { timeout: 15_000 });

    // clear and load it back through the open modal
    await setClassicScript(page, '');
    await page.click('#open-script');
    await expect(page.locator('#open-script-modal')).toBeVisible();
    await page.click(`#open-script-list a:has-text("${name}.groovy")`);

    await expect(page.locator('#message-success')).toContainText('Script loaded successfully.', { timeout: 15_000 });
    expect(await getClassicScript(page)).toContain(`classic save test ${name}`);
  });

  test('rejects invalid file names', async ({ page }) => {
    await page.goto(CLASSIC_URL);
    await expect(page.locator('#script-editor')).toBeVisible({ timeout: 30_000 });

    await setClassicScript(page, 'println "invalid name test"');

    await page.click('#save-script');
    await expect(page.locator('#save-script-modal')).toBeVisible();
    await page.fill('#save-script-file-name', 'bad name!');
    await page.click('#save-script-confirm');

    await expect(page.locator('#save-script-error')).toBeVisible();
    // modal stays open
    await expect(page.locator('#save-script-modal')).toBeVisible();
  });
});
