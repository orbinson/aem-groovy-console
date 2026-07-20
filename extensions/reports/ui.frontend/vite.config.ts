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
  // Reports assets live under a reports-owned JCR path (the core console owns /apps/groovyconsole, whose
  // content-package filter would otherwise wipe anything nested under it on reinstall).
  base: '/apps/groovyconsole-reports/spa/',
  resolve: {
    alias: {
      // Shared console infrastructure (API client, Monaco/Groovy editor setup, config, state, utils) stays in
      // the core ui.frontend; the reports UI imports it from there instead of duplicating it.
      '@console': resolve(__dirname, '../../../ui.frontend/src'),
    },
  },
  build: {
    outDir: '../ui.apps/src/main/content/jcr_root/apps/groovyconsole-reports/spa',
    emptyOutDir: true,
    target: 'es2021',
    chunkSizeWarningLimit: 4000,
    rollupOptions: {
      input: {
        // the business-facing reports UI (reports.html)
        reports: resolve(__dirname, 'reports.html'),
        // console UI extension module, dynamically imported by the console when the reports bundle is installed
        'reports-panel': resolve(__dirname, 'src/reports-console-panel.ts'),
      },
      output: {
        // Entry points and their CSS keep stable names so the page servlet / extension provider can reference
        // them without a manifest; the servlet cache-busts those with a per-deploy ?v token. Shared chunks are
        // content-hashed, so a code change gets a fresh URL and a redeploy needs no manual browser reload.
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name][extname]',
        // Monaco is lazy-loaded only by the editor view; keep it in its own stable chunk and keep Vite's
        // preload helper out of it so a dynamic import does not statically drag Monaco in.
        manualChunks: (id) => {
          if (id.includes('monaco-editor')) {
            return 'monaco';
          }
          if (id.includes('vite/preload-helper') || id.includes('vite/modulepreload-polyfill')) {
            return 'preload';
          }
          return undefined;
        },
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
