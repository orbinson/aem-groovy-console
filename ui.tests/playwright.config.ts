import { defineConfig } from '@playwright/test';

// Target instance: the it.tests feature-model launcher (Sling on 8080) or any running
// AEM/Sling instance with the Groovy Console installed.
const baseURL = process.env.GC_BASE_URL || 'http://localhost:8080';

export default defineConfig({
  testDir: './specs',
  globalSetup: './global-setup.ts',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['line']] : 'line',
  timeout: 60_000,
  use: {
    baseURL,
    // Sling answers 404 (not a 401 challenge) for anonymous /apps requests, so credentials
    // must be sent proactively on every request rather than via httpCredentials.
    extraHTTPHeaders: {
      Authorization: `Basic ${Buffer.from(
        `${process.env.GC_USERNAME || 'admin'}:${process.env.GC_PASSWORD || 'admin'}`,
      ).toString('base64')}`,
    },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
