import { expect, test } from '@playwright/test';
import { openDrawer, openModernUi, runScript } from './helpers';

test.describe('history', () => {
  test('shows executed scripts in the drawer and loads them back', async ({ page }) => {
    await openModernUi(page);

    const marker = `history-test-${Date.now()}`;
    await runScript(page, `println "${marker}"`);
    await expect(page.locator('.gc-dock .gc-dock-pre')).toContainText(marker, { timeout: 30_000 });

    await openDrawer(page, 'History');

    const history = page.locator('gc-history');
    const markerRow = history.locator('.gc-table tbody tr', { hasText: marker });
    await expect(markerRow).toBeVisible({ timeout: 15_000 });

    // load the record back: drawer closes, dock shows the output again
    await markerRow.getByRole('button', { name: 'Load' }).click();
    await expect(page.locator('gc-drawer[open]')).toHaveCount(0);
    await expect(page.locator('.gc-dock .gc-dock-pre')).toContainText(marker);
  });

  test('deletes an audit record', async ({ page }) => {
    await openModernUi(page);

    const marker = `delete-test-${Date.now()}`;
    await runScript(page, `println "${marker}"`);
    await expect(page.locator('.gc-dock .gc-dock-pre')).toContainText(marker, { timeout: 30_000 });

    await openDrawer(page, 'History');

    const history = page.locator('gc-history');
    const markerRow = history.locator('.gc-table tbody tr', { hasText: marker });

    await expect(markerRow).toBeVisible({ timeout: 15_000 });
    await markerRow.getByRole('button', { name: 'Delete' }).click();

    await expect(page.locator('sp-toast')).toContainText('Audit record deleted successfully.', { timeout: 15_000 });
    await expect(markerRow).toHaveCount(0, { timeout: 15_000 });
  });
});
