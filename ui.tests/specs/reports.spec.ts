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

  test('prefills a parameter from the URL, overriding its default', async ({ page }) => {
    // the sample report's PATH parameter defaults to /content; a URL prefill must win over that default
    await page.goto(`${REPORTS_URL}#/report/sample-content-listing?path=${encodeURIComponent('/apps')}`);

    await expect(page.getByRole('heading', { name: SAMPLE_TITLE })).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('.gcr-path-input')).toHaveValue('/apps');

    // prefill alone must not execute the report — no result table until the user runs it
    await expect(page.locator('table.gcr-table')).toHaveCount(0);
  });

  test('autoruns the report when the URL requests it', async ({ page }) => {
    // autorun=true executes the prefilled report on load, without the user clicking "Run report"
    await page.goto(
      `${REPORTS_URL}#/report/sample-content-listing?path=${encodeURIComponent('/content')}&autorun=true`,
    );

    const table = page.locator('table.gcr-table');
    await expect(table).toBeVisible({ timeout: 30_000 });
    await expect(table.locator('tbody tr').first()).toBeVisible();
  });

  test('seeds a multi-value parameter from repeated URL keys', async ({ page }) => {
    // sample-advanced-parameters echoes its submitted params; "names" is a multiple STRING parameter.
    // Repeating the key must submit both values as a list, which the echo report joins with ", ".
    await page.goto(
      `${REPORTS_URL}#/report/sample-advanced-parameters?names=alpha&names=beta&autorun=true`,
    );

    const table = page.locator('table.gcr-table');
    await expect(table).toBeVisible({ timeout: 30_000 });
    const namesRow = table.locator('tbody tr', { hasText: 'names' }).first();
    await expect(namesRow).toContainText('alpha, beta');
  });

  test('opens the edit view with a working code editor', async ({ page }) => {
    // the edit view lazy-loads the Monaco code editor; the sample report also has a PATH parameter, so the
    // path browser lazy-loads too. Both must initialise — this guards against a chunk-load clash that leaves
    // the <gcr-code-editor> host element on the page but never upgraded (no Monaco, no script field).
    await page.goto(`${REPORTS_URL}#/report/sample-content-listing/edit`);

    await expect(page.getByRole('heading', { name: new RegExp(`Edit: ${SAMPLE_TITLE}`) })).toBeVisible({
      timeout: 30_000,
    });

    // the Monaco editor must actually render, not just the host element
    await expect(page.locator('gcr-code-editor .monaco-editor')).toBeVisible({ timeout: 30_000 });

    // the scheduling and distribution sections are part of the editor
    await expect(page.getByRole('heading', { name: 'Schedule' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Distribution' })).toBeVisible();
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
