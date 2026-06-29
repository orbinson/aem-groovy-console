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
  // All built asset URLs are absolute under the JCR path the SPA is deployed to.
  base: '/apps/groovyconsole/spa/',
  build: {
    outDir: '../ui.apps/src/main/content/jcr_root/apps/groovyconsole/spa',
    emptyOutDir: true,
    target: 'es2021',
    chunkSizeWarningLimit: 4000,
    rollupOptions: {
      input: {
        // console SPA (modern.html) and the business-facing reports UI (reports.html)
        index: resolve(__dirname, 'index.html'),
        reports: resolve(__dirname, 'reports.html'),
        // console UI extension module, dynamically imported when the reports bundle is installed
        'reports-panel': resolve(__dirname, 'src/reports-console-panel.ts'),
      },
      output: {
        // Stable file names so modern.html/reports.html can reference them without a manifest.
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name][extname]',
        // Monaco is shared by several entries; give the chunk (and its CSS) a stable name the
        // HTL entry pages can link to.  Vite's preload helper must NOT end up inside the monaco
        // chunk, or every dynamic import would statically drag Monaco in (the reports page
        // lazy-loads Monaco only for the editor view).
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
