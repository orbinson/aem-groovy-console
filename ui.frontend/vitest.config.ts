import { defineConfig } from 'vitest/config';

// Separate from vite.config.ts: unit tests don't need the multi-entry build / JCR base path.
export default defineConfig({
  test: {
    // jsdom provides window/location for modules (e.g. config.ts) loaded by the units under test
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
  },
});
