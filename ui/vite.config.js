import { fileURLToPath } from 'node:url';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const polarionUrl = env.VITE_BASE_URL || 'http://localhost';

  // Dedupe so the app and @grigoriev/react-sbb-polarion resolve to this app's single instance of
  // each: React (two copies mean "invalid hook call") and sonner (the RSP `Toaster` host and the
  // toasts RSP components fire must share one instance, or the toasts never reach the host).
  const resolve = { dedupe: ['react', 'react-dom', 'sonner'] };

  if (command === 'serve') {
    return {
      plugins: [react()],
      resolve,
      server: {
        proxy: {
          // Generic UI toolkit (SearchableDropdown JS + its CSS) served by GenericUiServlet. Served
          // unauthenticated in Polarion (see the docx-exporter-app web.xml), so the dev proxy can fetch
          // it without a session.
          '/polarion/docx-exporter-app/ui/generic': {
            target: polarionUrl,
            changeOrigin: true,
          },
          // The build-generated help articles served straight from the app webapp; the Usage
          // Disclaimer page reads disclaimer.html from here (there is no REST endpoint for it).
          '/polarion/docx-exporter-app/ui/html': {
            target: polarionUrl,
            changeOrigin: true,
          },
          // The extension's own webapp context: its REST API, which the About page reads.
          '/polarion/docx-exporter/rest': {
            target: polarionUrl,
            changeOrigin: true,
          },
          // The product JS and assets still served from the extension's own webapp, not from this app.
          '/polarion/docx-exporter/ui': {
            target: polarionUrl,
            changeOrigin: true,
          },
          '/polarion/rest': {
            target: polarionUrl,
            changeOrigin: true,
          },
          '/polarion/ria': {
            target: polarionUrl,
            changeOrigin: true,
          },
          '/polarion/icons': {
            target: polarionUrl,
            changeOrigin: true,
          },
        },
      },
    };
  }

  return {
    plugins: [react()],
    resolve,
    // Never let a developer's personal access token reach a shipped bundle. VITE_BEARER_TOKEN is a
    // `vite dev` convenience (it switches useRemote to the token-authenticated /api endpoints); Vite
    // inlines import.meta.env.VITE_* at build time, so a local .env.local would otherwise be baked
    // into the bundle that `mvn -P install-to-local-polarion` deploys, readable by everyone the SPA is
    // served to. Forcing it undefined here keeps production on the session-authenticated /internal
    // endpoints, which is what Polarion provides anyway.
    define: { 'import.meta.env.VITE_BEARER_TOKEN': 'undefined' },
    base: '/polarion/docx-exporter-app/ui/app/',
    build: {
      outDir: './dist/app',
      emptyOutDir: true,
      rollupOptions: {
        // Keep what an entry exports. A Vite app build assumes its entries are only ever executed, so it
        // drops their exports - which would leave the side panel bundle without the `mountSidePanel` its
        // importer calls. scripts/check-runtime-entries.mjs guards that after every build, since no test
        // sees the built files.
        preserveEntrySignatures: 'strict',
        // Two entries: the admin SPA (index.html), and the Document Properties side panel imported at
        // runtime by the form-extension fragment in the document editor.
        input: {
          index: fileURLToPath(new URL('index.html', import.meta.url)),
          'side-panel': fileURLToPath(new URL('src/sidepanel/mount.tsx', import.meta.url)),
        },
        output: {
          // The side panel's file name must stay predictable: its importer names it by URL and cannot know
          // the hash Vite would append. It appends the extension version instead, which is what busts the
          // browser cache on an update.
          entryFileNames: (chunk) => (chunk.name === 'side-panel' ? 'assets/side-panel.js' : 'assets/[name]-[hash].js'),
          // What the entries share (React above all) lands in one chunk. Rollup would name it after
          // whichever module it happened to pick, which reads as nonsense next to side-panel.js.
          chunkFileNames: 'assets/shared-[hash].js',
        },
      },
    },
  };
});
