import { expect, test } from '@playwright/test';
import { CLASSIC_URL, openModernUi, setScriptContent } from './helpers';

test.describe('streaming output', () => {
  test('modern UI shows live output while a script runs', async ({ page }) => {
    await openModernUi(page);

    await setScriptContent(page, '6.times { println "stream $it"; Thread.sleep(500) }\nreturn "streamed"');
    await page.getByRole('button', { name: 'Run', exact: true }).click();

    const dock = page.locator('.gc-dock');

    // partial output appears while the script is still running
    await expect(dock.locator('.gc-dock-live', { hasText: 'Live' })).toBeVisible({ timeout: 10_000 });
    await expect(dock.locator('.gc-dock-pre')).toContainText('stream 0', { timeout: 10_000 });
    await expect(page.locator('.gc-status-bar')).toContainText('Running');

    // on completion the Log tab is selected by default and holds the full output
    await expect(dock.locator('.gc-dock-tab.is-active')).toHaveText('Log', { timeout: 30_000 });
    await expect(page.locator('.gc-status-bar')).toContainText('Ready');
    await expect(dock.locator('.gc-dock-pre')).toContainText('stream 5');

    // the returned value is available on the secondary Result tab
    await dock.locator('.gc-dock-tab', { hasText: 'Result' }).click();
    await expect(dock.locator('.gc-dock-pre')).toContainText('streamed');
  });

  test('classic UI shows live output while a script runs', async ({ page }) => {
    await page.goto(CLASSIC_URL);
    await expect(page.locator('#script-editor')).toBeVisible({ timeout: 30_000 });

    await page.evaluate(() => {
      (window as unknown as { scriptEditor: { getSession(): { setValue(v: string): void } } })
        .scriptEditor.getSession().setValue('4.times { println "ctick $it"; Thread.sleep(600) }');
    });
    await page.click('#run-script');

    // partial output appears while still running
    await expect(page.locator('#output pre')).toContainText('ctick 0', { timeout: 10_000 });
    await expect(page.locator('#run-script-text')).toHaveText('Running...');

    // run completes: full output, button restored
    await expect(page.locator('#output pre')).toContainText('ctick 3', { timeout: 30_000 });
    await expect(page.locator('#run-script-text')).toHaveText('Run Script', { timeout: 15_000 });
  });
});
