import { expect, test } from '@playwright/test';

const REPORTS_URL = '/apps/groovyconsole/reports.html';
const MODERN_URL = '/apps/groovyconsole.modern.html';

const SAMPLE_TITLE = 'Sample: Content listing';

test.describe('reports', () => {
  test('lists, runs and exports the sample report', async ({ page }) => {
    await page.goto(REPORTS_URL);

    // the catalogue lists the sample report shipped with the reports extension
    const sample = page.getByRole('link', { name: new RegExp(SAMPLE_TITLE) }).first();
    await expect(sample).toBeVisible({ timeout: 30_000 });
    await sample.click();

    // run view: heading + the PATH parameter form (default /content)
    await expect(page.getByRole('heading', { name: SAMPLE_TITLE })).toBeVisible();
    await expect(page.locator('.gcr-path-input')).toBeVisible();

    // run the report synchronously
    await page.getByRole('button', { name: 'Run report' }).click();

    // the typed result table renders with the declared columns and at least one row
    const table = page.locator('table.gcr-table');
    await expect(table).toBeVisible({ timeout: 30_000 });
    await expect(table.locator('th', { hasText: 'Name' })).toBeVisible();
    // the UI-only "Edit" column is shown in the table (it is excluded only from exports)
    await expect(table.locator('th', { hasText: 'Edit' })).toBeVisible();
    await expect(table.locator('tbody tr').first()).toBeVisible();

    // export actions are API-driven (csv always; xlsx when a POI provider is present)
    await expect(page.locator('.gcr-result-actions')).toContainText('Download CSV');
  });

  test('registers a Reports panel in the modern console', async ({ page }) => {
    await page.goto(MODERN_URL);

    // the reports extension contributes a rail panel via ConsoleUiExtensionProvider
    const reportsRail = page.locator('.gc-rail-button[title="Reports"]');
    await expect(reportsRail).toBeVisible({ timeout: 30_000 });
    await reportsRail.click();

    // the drawer (gc-reports, shadow DOM) lists the sample report; getByText pierces the shadow root
    await expect(page.getByText(SAMPLE_TITLE).first()).toBeVisible({ timeout: 15_000 });
  });
});
