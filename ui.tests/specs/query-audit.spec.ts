import { expect, test } from '@playwright/test';
import { openDrawer, openModernUi, setScriptContent } from './helpers';

test.describe('query audit', () => {
  test('registers a Query audit panel in the modern console', async ({ page }) => {
    await openModernUi(page);

    // the query-audit extension contributes a rail panel via ConsoleUiExtensionProvider
    await expect(page.locator('.gc-rail-button[title="Query audit"]')).toBeVisible({ timeout: 30_000 });
  });

  test('audits the active editor script and flags an un-indexed query', async ({ page }) => {
    await openModernUi(page);

    // a query on a custom (un-indexed) property -> Oak must traverse -> flagged as needing an index
    await setScriptContent(
      page,
      "sql2Query(\"SELECT * FROM [nt:base] AS n WHERE ISDESCENDANTNODE(n, '/content') AND n.[qaMarker] = 'x'\")",
    );

    await openDrawer(page, 'Query audit');
    await page.getByRole('button', { name: 'Audit active script' }).click();

    // the panel renders a row for the executed query, flagged "needs index", with the statement and Oak plan
    await expect(page.locator('query-audit-panel .needs')).toContainText('needs index', { timeout: 20_000 });
    await expect(page.locator('query-audit-panel')).toContainText('qaMarker');
    await expect(page.locator('query-audit-panel')).toContainText('traverse');
  });

  test('reports nothing to audit when the editor is empty', async ({ page }) => {
    await openModernUi(page);
    await setScriptContent(page, '');

    await openDrawer(page, 'Query audit');
    await page.getByRole('button', { name: 'Audit active script' }).click();

    await expect(page.locator('query-audit-panel')).toContainText('editor is empty');
  });
});
