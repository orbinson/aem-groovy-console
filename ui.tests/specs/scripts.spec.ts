import { expect, test } from '@playwright/test';
import { clickMenuItem, expectToast, getScriptContent, openModernUi, setScriptContent } from './helpers';

const SCRIPT_NAME = `pw-test-${Date.now()}`;
const SCRIPT_CONTENT = `// playwright save test\nreturn "${SCRIPT_NAME}"`;

test.describe.serial('script save and open', () => {
  test('saves a script through the save dialog', async ({ page }) => {
    await openModernUi(page);

    await setScriptContent(page, SCRIPT_CONTENT);
    await clickMenuItem(page, 'Save…');

    const dialog = page.locator('gc-save-dialog sp-dialog-wrapper');
    await expect(dialog).toBeVisible();

    await dialog.locator('sp-textfield').click();
    await page.keyboard.press('ControlOrMeta+a');
    await page.keyboard.type(SCRIPT_NAME);
    await dialog.getByRole('button', { name: 'Save' }).click();

    await expectToast(page, 'Script saved successfully.');
    // script name appears in the app bar without a dirty dot
    await expect(page.locator('.gc-script-name')).toContainText(SCRIPT_NAME);
  });

  test('opens the saved script through the script browser', async ({ page }) => {
    await openModernUi(page);

    await setScriptContent(page, '');
    await clickMenuItem(page, 'Open…');

    const dialog = page.locator('gc-script-browser-dialog sp-dialog-wrapper');
    await expect(dialog).toBeVisible();

    await dialog.locator('.gc-browser-item', { hasText: `${SCRIPT_NAME}.groovy` }).click();

    await expectToast(page, 'Script loaded successfully.');
    expect(await getScriptContent(page)).toContain('playwright save test');
  });
});
