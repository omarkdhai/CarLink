import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    // Proxy API calls to the Spring Boot backend during dev. The SPA origin
    // stays consistent so session/token flows behave as in production.
    // NOTE: /c/* (the public QR contact page) is intentionally NOT proxied —
    // the React app owns that route (see features/contact). In production the
    // static host must fall back to index.html for /c/*.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})