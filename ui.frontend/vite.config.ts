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
    // emit a manifest so the console page (ModernConsoleConfig) can link the content-hashed entry for
    // cache-busting; written at the spa root as manifest.json
    manifest: 'manifest.json',
    rollupOptions: {
      input: {
        // console SPA (modern.html)
        index: resolve(__dirname, 'index.html'),
      },
      output: {
        // Content-hash the entry, chunks and assets so a changed file gets a fresh URL and an unchanged one stays
        // cached; the console page resolves the hashed names (incl. the Monaco chunk's CSS) from the manifest.
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
        // Give the Monaco chunk a stable name the HTL entry page can link to.  Vite's preload helper must
        // NOT end up inside the monaco chunk, or every dynamic import would statically drag Monaco in.
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
