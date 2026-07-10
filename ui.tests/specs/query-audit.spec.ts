import { expect, test } from '@playwright/test';
import { expectToast, openModernUi, setScriptContent } from './helpers';

/** Open the Run options menu (the chevron next to Run) and click the given action. */
async function clickRunAction(page: Parameters<typeof openModernUi>[0], label: string): Promise<void> {
  await page.locator('.gc-run-group .gc-run-more').click();
  await page.locator('.gc-run-group .gc-overflow-menu button', { hasText: label }).click();
}

test.describe('query audit', () => {
  test('adds "Run with query audit" to the run options menu', async ({ page }) => {
    await openModernUi(page);

    // the query-audit extension registers a run action; the chevron only renders when actions exist
    const chevron = page.locator('.gc-run-group .gc-run-more');
    await expect(chevron).toBeVisible({ timeout: 30_000 });

    await chevron.click();
    await expect(page.locator('.gc-run-group .gc-overflow-menu')).toContainText('Run with query audit');
  });

  test('runs the script and flags an un-indexed query in the Query audit tab', async ({ page }) => {
    await openModernUi(page);

    // a query on a custom (un-indexed) property -> Oak must traverse -> flagged as needing an index
    await setScriptContent(
      page,
      "sql2Query(\"SELECT * FROM [nt:base] AS n WHERE ISDESCENDANTNODE(n, '/content') AND n.[qaMarker] = 'x'\")",
    );

    await clickRunAction(page, 'Run with query audit');

    // the audit result opens as a tab in the output dock, next to Log/Result
    await expect(page.locator('.gc-dock-tab', { hasText: 'Query audit' })).toBeVisible({ timeout: 20_000 });
    const result = page.locator('query-audit-result');
    await expect(result.locator('.qa-needs')).toContainText('needs index');
    await expect(result).toContainText('qaMarker');
    await expect(result.locator('.qa-lang')).toContainText('JCR-SQL2');
    await result.locator('.qa-plan summary').click();
    await expect(result).toContainText('traverse');
  });

  test('shows a toast when auditing an empty editor', async ({ page }) => {
    await openModernUi(page);
    await setScriptContent(page, '');

    await clickRunAction(page, 'Run with query audit');

    await expectToast(page, 'Script is empty.');
  });
});
