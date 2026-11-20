import { expect, test, type Page } from '@playwright/test';

async function waitForEditor(page: Page): Promise<void> {
  await page.waitForFunction(
    () => typeof (window as any).scriptEditor?.getSession === 'function',
    undefined,
    { timeout: 30_000 },
  );
}

test('classic console runs a script and displays its result', async ({ page }) => {
  await page.goto('/apps/groovyconsole.html');
  await waitForEditor(page);

  await page.evaluate(() => {
    (window as any).scriptEditor.getSession().setValue("return 'playwright-19x'");
  });
  await page.locator('#run-script').click();

  await expect(page.locator('#result')).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('#result pre')).toHaveText('playwright-19x');
});
