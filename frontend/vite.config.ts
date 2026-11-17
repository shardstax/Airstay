import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// The browser only ever talks to its own origin: /api is proxied to the backend (here in dev, by Nginx in Docker).
// That keeps the refresh-token cookie (SameSite=Strict, path /api/v1/auth) a first-party cookie and needs no CORS.
const apiTarget = process.env.API_PROXY_TARGET ?? 'http://app:8080';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    port: 5173,
    proxy: { '/api': { target: apiTarget, changeOrigin: false } },
    // File events do not cross the Windows/Docker boundary reliably; set VITE_POLLING=1 for the dev container.
    watch: process.env.VITE_POLLING ? { usePolling: true, interval: 300 } : undefined,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
