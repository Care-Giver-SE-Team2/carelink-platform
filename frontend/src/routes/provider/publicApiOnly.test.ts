import { describe, expect, it } from 'vitest'

/**
 * PT04 requires the Partner Portal to use only the public provider API. These imports would reach
 * CareLink's internal API or its login instead, so none of the portal's files may have them.
 */
const FORBIDDEN = [
  /from\s+['"][^'"]*shared\/api/,
  /from\s+['"][^'"]*features\/(?!provider-platform)[\w-]+\/api/,
  /from\s+['"][^'"]*RequireRole/,
  /from\s+['"][^'"]*features\/auth/,
  /fetch\(\s*['`"]\/api/,
]

const sources = {
  ...import.meta.glob(['./**/*.{ts,tsx}', '!./**/*.test.{ts,tsx}'], { query: '?raw', import: 'default', eager: true }),
  ...import.meta.glob(['../../features/provider-platform/*.ts', '!../../features/provider-platform/*.test.ts'], {
    query: '?raw',
    import: 'default',
    eager: true,
  }),
} as Record<string, string>

describe('the Partner Portal uses only the public provider API', () => {
  it('finds the Partner Portal sources', () => {
    expect(Object.keys(sources)).toEqual(expect.arrayContaining(['./pages/Jobs.tsx', '../../features/provider-platform/api.ts']))
  })

  it.each(Object.entries(sources))('%s imports nothing internal', (_file, source) => {
    for (const pattern of FORBIDDEN) expect(source).not.toMatch(pattern)
  })
})
