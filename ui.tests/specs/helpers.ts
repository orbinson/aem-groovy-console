import { expect, type Page } from '@playwright/test';

export const MODERN_URL = '/apps/groovyconsole.modern.html';
export const CLASSIC_URL = '/apps/groovyconsole.classic.html';
export const DEFAULT_URL = '/apps/groovyconsole.html';

/** Open the modern UI and wait for the Monaco script editor to initialize. */
export async function openModernUi(page: Page): Promise<void> {
  await page.goto(MODERN_URL);
  await expect(page.locator('gc-script-editor .monaco-editor').first()).toBeVisible({ timeout: 30_000 });
}

/**
 * Set the script editor content directly through the gc-script-editor element (light DOM),
 * bypassing Monaco auto-closing pairs that make keyboard.type unreliable for exact content.
 */
export async function setScriptContent(page: Page, content: string): Promise<void> {
  await page.evaluate((script) => {
    const editor = document.querySelector('gc-script-editor') as HTMLElement & { value: string };
    editor.value = script;
  }, content);
}

export async function getScriptContent(page: Page): Promise<string> {
  return page.evaluate(() => {
    const editor = document.querySelector('gc-script-editor') as HTMLElement & { value: string };
    return editor.value;
  });
}

export async function runScript(page: Page, script: string): Promise<void> {
  await setScriptContent(page, script);
  await page.getByRole('button', { name: 'Run', exact: true }).click();
}

/** Open the app-bar File menu and click the given item. */
export async function clickMenuItem(page: Page, item: string): Promise<void> {
  await page.locator('.gc-overflow sp-action-button').click();
  await page.locator('.gc-overflow-menu button', { hasText: item }).click();
}

/** Open an activity-rail drawer by its title. */
export async function openDrawer(page: Page, title: string): Promise<void> {
  await page.locator(`.gc-rail-button[title="${title}"]`).click();
  await expect(page.locator('gc-drawer[open]')).toBeVisible();
}

export async function expectToast(page: Page, message: string): Promise<void> {
  await expect(page.locator('sp-toast')).toContainText(message, { timeout: 15_000 });
}
