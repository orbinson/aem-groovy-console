import { expect, test } from '@playwright/test';
import { CLASSIC_URL, DEFAULT_URL, MODERN_URL } from './helpers';

test.describe('UI routing', () => {
  test('default path serves the classic UI (defaultUi=classic)', async ({ page }) => {
    await page.goto(DEFAULT_URL);

    await expect(page.locator('#script-editor')).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('gc-app')).toHaveCount(0);
  });

  test('classic selector always serves the classic UI', async ({ page }) => {
    await page.goto(CLASSIC_URL);

    await expect(page.locator('#script-editor')).toBeVisible({ timeout: 30_000 });
  });

  test('modern selector always serves the modern UI', async ({ page }) => {
    await page.goto(MODERN_URL);

    await expect(page.locator('gc-app')).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('#script-editor')).toHaveCount(0);
  });

  test('classic UI links to the modern UI', async ({ page }) => {
    await page.goto(CLASSIC_URL);

    const link = page.locator('a', { hasText: 'Try the new UI' });
    await expect(link).toBeVisible();
    await link.click();

    await expect(page.locator('gc-app')).toBeVisible({ timeout: 30_000 });
  });

  test('modern UI links back to the classic UI via the help drawer', async ({ page }) => {
    await page.goto(MODERN_URL);

    await page.locator('.gc-rail-button[title="Help & Reference"]').click();
    await page.locator('.gc-classic-link').click();

    await expect(page.locator('#script-editor')).toBeVisible({ timeout: 30_000 });
  });
});
