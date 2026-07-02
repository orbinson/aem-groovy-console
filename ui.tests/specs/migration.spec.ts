import { expect, test } from '@playwright/test';

const MIGRATIONS_URL = '/apps/groovyconsole/migrations.html';
const MODERN_URL = '/apps/groovyconsole.modern.html';

test.describe('migration', () => {
  test('serves the migration history UI', async ({ page }) => {
    await page.goto(MIGRATIONS_URL);

    // run history and script registry sections render (no scripts deployed by default)
    await expect(page.getByRole('heading', { name: 'Run history' })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole('heading', { name: 'Scripts' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Dry run' })).toBeVisible();
  });

  test('registers a Migrations panel in the modern console', async ({ page }) => {
    await page.goto(MODERN_URL);

    // the migration extension contributes a rail panel via ConsoleUiExtensionProvider
    const migrationsRail = page.locator('.gc-rail-button[title="Migrations"]');
    await expect(migrationsRail).toBeVisible({ timeout: 30_000 });
    await migrationsRail.click();

    // the drawer (gc-migration, shadow DOM) shows the pending scripts section; getByText pierces the shadow root
    await expect(page.getByText('Pending scripts').first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Recent runs').first()).toBeVisible();
  });
});
