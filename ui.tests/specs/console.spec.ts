import { expect, test } from '@playwright/test';
import { openModernUi, runScript } from './helpers';

test.describe('console', () => {
  test('loads the IDE shell with Monaco editor', async ({ page }) => {
    await openModernUi(page);

    await expect(page.locator('gc-app')).toBeVisible();
    await expect(page.locator('.gc-app-bar')).toContainText('Groovy Console');
    await expect(page.getByRole('button', { name: 'Run', exact: true })).toBeVisible();
    await expect(page.locator('.gc-status-bar')).toContainText('Ready');
    await expect(page.locator('.gc-dock')).toContainText('Run a script');
  });

  test('runs a script and shows log output and running time', async ({ page }) => {
    await openModernUi(page);

    await runScript(page, 'println "hello from playwright"');

    const dock = page.locator('.gc-dock');
    await expect(dock.locator('.gc-dock-tab.is-active')).toHaveText('Log', { timeout: 30_000 });
    await expect(dock.locator('.gc-dock-pre')).toContainText('hello from playwright');
    await expect(dock.locator('sp-badge')).toBeVisible();
  });

  test('shows result for returned values', async ({ page }) => {
    await openModernUi(page);

    await runScript(page, 'return 6 * 7');

    const dock = page.locator('.gc-dock');
    await expect(dock.locator('.gc-dock-tab.is-active')).toHaveText('Result', { timeout: 30_000 });
    await expect(dock.locator('.gc-dock-pre')).toContainText('42');
  });

  test('shows stacktrace for runtime errors', async ({ page }) => {
    await openModernUi(page);

    await runScript(page, '1 / 0');

    const dock = page.locator('.gc-dock');
    await expect(dock.locator('.gc-dock-tab.is-active')).toHaveText('Trace', { timeout: 30_000 });
    await expect(dock.locator('.gc-dock-pre')).toContainText('ArithmeticException');
    await expect(page.locator('.gc-status-bar')).toContainText('Error');
  });

  test('stacktrace script frames navigate to the source line', async ({ page }) => {
    await openModernUi(page);

    await runScript(page, 'println "line one"\nprintln "line two"\n1 / 0');

    const dock = page.locator('.gc-dock');
    await expect(dock.locator('.gc-dock-tab.is-active')).toHaveText('Trace', { timeout: 30_000 });

    // the script frame (ScriptN.groovy:3) is rendered as a clickable link
    const frameLink = dock.locator('.gc-trace-link', { hasText: '.groovy:3' }).first();
    await expect(frameLink).toBeVisible();
    await frameLink.click();

    // the editor cursor jumps to the failing line
    await expect(page.locator('.gc-status-bar')).toContainText('Ln 3', { timeout: 10_000 });
  });

  test('renders table results', async ({ page }) => {
    await openModernUi(page);

    await runScript(page, "table {\n columns 'Name', 'Value'\n row 'foo', '1'\n}");

    const dock = page.locator('.gc-dock');
    await expect(dock.locator('.gc-dock-tab.is-active')).toHaveText('Table', { timeout: 30_000 });
    await expect(dock.locator('sp-table')).toContainText('foo');
  });
});
