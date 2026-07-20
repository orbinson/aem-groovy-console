import { resolve } from 'node:path';
import { defineConfig } from 'vite';

// Local Sling (8080) or AEM author (4502) instance the dev server proxies API calls to.
const target = process.env.GC_PROXY_TARGET || 'http://localhost:8080';

const proxy = {
  target,
  changeOrigin: true,
  auth: 'admin:admin',
  // Setting the Origin header to the target bypasses the Sling referrer filter / AEM CSRF filter for dev POSTs.
  headers: { Origin: target },
};

export default defineConfig({
  // Migration assets live under a migration-owned JCR path (the core console owns /apps/groovyconsole, whose
  // content-package filter would otherwise wipe anything nested under it on reinstall).
  base: '/apps/groovyconsole-migration/spa/',
  resolve: {
    alias: {
      // Shared console infrastructure (API client, config, state) stays in the core ui.frontend; the
      // migration UI imports it from there instead of duplicating it.
      '@console': resolve(__dirname, '../../../ui.frontend/src'),
    },
  },
  build: {
    outDir: '../ui.apps/src/main/content/jcr_root/apps/groovyconsole-migration/spa',
    emptyOutDir: true,
    target: 'es2021',
    // emit a manifest so the page servlet and the console (for the panel URL) can link the content-hashed
    // entries for cache-busting; written at the spa root as manifest.json
    manifest: 'manifest.json',
    rollupOptions: {
      input: {
        // the migration history UI (migration.html)
        migration: resolve(__dirname, 'migration.html'),
        // console UI extension module, dynamically imported by the console when the migration bundle is installed
        'migration-panel': resolve(__dirname, 'src/migration-console-panel.ts'),
      },
      output: {
        // Content-hash the entries, chunks and assets for cache-busting; the page servlet and the console
        // resolve the hashed names from the manifest.
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
      },
    },
  },
  server: {
    proxy: {
      '/bin': proxy,
      '/conf': proxy,
      '/crx': proxy,
    },
  },
});
