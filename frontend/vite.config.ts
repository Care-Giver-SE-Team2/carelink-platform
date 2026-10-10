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
      // The report service (8082): weekly reports, value-added services, caregiver reviews. The
      // weekly summary shares /api/elders/{id} with core, so it is matched by its full path.
      '/api/reports': 'http://localhost:8082',
      '/api/value-added-service-requests': 'http://localhost:8082',
      '/api/family/caregiver-reviews': 'http://localhost:8082',
      '/api/family/value-added-service': 'http://localhost:8082',
      '/api/elders/me/value-added-service': 'http://localhost:8082',
      '^/api/elders/[^/]+/weekly-summary': 'http://localhost:8082',
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
