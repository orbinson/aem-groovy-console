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
      output: {
        // Stable file names so modern.html can reference them without a manifest.
        entryFileNames: 'assets/index.js',
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
