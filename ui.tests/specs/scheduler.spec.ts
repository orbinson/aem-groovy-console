import { expect, test } from '@playwright/test';
import { expectToast, openDrawer, openModernUi, setScriptContent } from './helpers';

test.describe('scheduler', () => {
  test('schedules and deletes a job from the jobs drawer', async ({ page }) => {
    await openModernUi(page);

    const jobTitle = `pw-job-${Date.now()}`;

    await setScriptContent(page, 'println "scheduled"');
    await openDrawer(page, 'Scheduled Jobs');

    const form = page.locator('gc-scheduler');
    await expect(form).toBeVisible();

    await form.locator('#gc-job-title').click();
    await page.keyboard.type(jobTitle);

    await form.locator('#gc-cron-expression').click();
    await page.keyboard.type('0 0 3 * * ?'); // daily at 03:00; deleted again below

    await form.getByRole('button', { name: 'Schedule Job' }).click();
    await expectToast(page, 'Job scheduled successfully.');

    // the job appears in the list below the form
    const jobs = page.locator('gc-scheduled-jobs');
    const row = jobs.locator('.gc-table tbody tr', { hasText: jobTitle });
    await expect(row).toBeVisible({ timeout: 15_000 });

    // delete it again
    await row.getByRole('button', { name: 'Delete' }).click();
    await expectToast(page, 'Scheduled job deleted successfully.');
    await expect(jobs.locator('.gc-table tbody tr', { hasText: jobTitle })).toHaveCount(0, { timeout: 15_000 });
  });

  test('requires a job title', async ({ page }) => {
    await openModernUi(page);

    await setScriptContent(page, 'println "no title"');
    await openDrawer(page, 'Scheduled Jobs');

    await page.locator('gc-scheduler').getByRole('button', { name: 'Schedule Job' }).click();

    await expectToast(page, 'Job Title is required.');
  });
});
