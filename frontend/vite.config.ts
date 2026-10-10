/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  // Proxy /api to the local backend during development, avoiding CORS configuration
  server: {
    proxy: {
      // The notification service (scripts/run.sh all starts it on 8083). Listed before /api: the
      // first matching entry wins.
      '/api/notifications': 'http://localhost:8083',
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    globals: true,
    // The default threads pool times out its workers on Windows; pin the forks pool
    pool: 'forks',
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      reportsDirectory: './coverage',
      exclude: ['**/*.config.*', '**/main.tsx', '**/*.d.ts', 'dist/**'],
    },
  },
})
