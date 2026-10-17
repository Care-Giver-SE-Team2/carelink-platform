/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [
    react(),
    {
      name: 'exclude-provider-mock-from-build',
      configResolved(config) {
        if (config.command === 'build' && config.env.VITE_OPEN_PLATFORM_MOCK === 'true') {
          throw new Error('Provider mock mode is for local development and tests, not publishable builds.')
        }
      },
      generateBundle(_options, bundle) {
        for (const output of Object.values(bundle)) {
          // Rolldown can retain unused scalar declarations in module metadata until minification.
          // Reject any used mock export or the mock's storage/seed state in the final chunk.
          if (output.type === 'chunk' && (output.code.includes('carelink.mock.providerPlatform.v1')
            || Object.entries(output.modules).some(([id, module]) =>
              id.replaceAll('\\', '/').endsWith('/features/provider-platform/mockPlatform.ts') && module.renderedExports.length > 0,
            ))) {
            this.error('Publishable builds must not contain the provider mock platform.')
          }
        }
      },
    },
  ],
  // Proxy /api to the local backend during development, avoiding CORS configuration
  server: {
    proxy: {
      // The notification service (scripts/run.sh all starts it on 8083). Listed before /api: the
      // first matching entry wins.
      '/api/notifications': 'http://localhost:8083',
      // The Open Platform (notification): the manager's provider screens, and the Partner Portal's
      // public API with its sign-in and tokens
      '/api/provider-applications': 'http://localhost:8083',
      '/api/providers': 'http://localhost:8083',
      '/api/open': 'http://localhost:8083',
      // The report service (8082): weekly reports, value-added services, caregiver reviews. The
      // weekly summary shares /api/elders/{id} with core, so it is matched by its full path.
      '/api/reports': 'http://localhost:8082',
      '/api/value-added-service-requests': 'http://localhost:8082',
      '/api/family/caregiver-reviews': 'http://localhost:8082',
      '/api/family/value-added-service': 'http://localhost:8082',
      '/api/elders/me/value-added-service': 'http://localhost:8082',
      '^/api/elders/[^/]+/weekly-summary': 'http://localhost:8082',
      // The visit service (8081): visits, their execution, the elder's confirmation, the family's
      // view of caregivers. /api/caregivers is shared with core, so visit's paths there are
      // matched in full: the caregiver's own page, schedule and incident reports, and a
      // caregiver's public profile and certificates.
      '/api/visits': 'http://localhost:8081',
      '/api/elders/me/visits': 'http://localhost:8081',
      '^/api/caregivers/me([?].*)?$': 'http://localhost:8081',
      '/api/caregivers/me/schedule': 'http://localhost:8081',
      '/api/caregivers/me/incidents': 'http://localhost:8081',
      '^/api/caregivers/[0-9]+(/credentials)?([?].*)?$': 'http://localhost:8081',
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
