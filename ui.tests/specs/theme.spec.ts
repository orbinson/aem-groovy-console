import { expect, test } from '@playwright/test';
import { openModernUi } from './helpers';

test.describe('theme', () => {
  test('toggles between light and dark and persists across reloads', async ({ page }) => {
    await openModernUi(page);

    const theme = page.locator('sp-theme');
    await expect(theme).toHaveAttribute('color', 'light');

    await page.locator('gc-app-bar sp-switch').click();
    await expect(theme).toHaveAttribute('color', 'dark');

    await page.reload();
    await expect(page.locator('sp-theme')).toHaveAttribute('color', 'dark', { timeout: 30_000 });

    // back to light for subsequent tests
    await page.locator('gc-app-bar sp-switch').click();
    await expect(page.locator('sp-theme')).toHaveAttribute('color', 'light');
  });
});
