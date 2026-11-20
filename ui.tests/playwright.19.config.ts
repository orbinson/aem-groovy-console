import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './specs',
  testMatch: '**/classic-19.spec.ts',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? 'line' : 'list',
  timeout: 60_000,
  use: {
    baseURL: process.env.GC_BASE_URL || 'http://localhost:8080',
    extraHTTPHeaders: {
      Authorization: `Basic ${Buffer.from(
        `${process.env.GC_USERNAME || 'admin'}:${process.env.GC_PASSWORD || 'admin'}`,
      ).toString('base64')}`,
    },
  },
});
