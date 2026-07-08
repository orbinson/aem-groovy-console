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
      // Minimal self-contained shim for the tiny bit of shared console frontend infrastructure this UI uses
      // (API client, config, color-scheme persistence). This line does not ship the modern UI/its ui.frontend
      // module, so — unlike main — this points at a small local copy instead of a sibling module.
      '@console': resolve(__dirname, 'src/console-shim'),
    },
  },
  build: {
    outDir: '../ui.apps/src/main/content/jcr_root/apps/groovyconsole-migration/spa',
    emptyOutDir: true,
    target: 'es2021',
    rollupOptions: {
      input: {
        // the migration history UI (migration.html)
        migration: resolve(__dirname, 'migration.html'),
      },
      output: {
        // Stable file names so migration.html can reference them without a manifest.
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name][extname]',
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
