// @vitest-environment node
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { build } from 'vite'
import config from './vite.config'

let directory: string

beforeEach(async () => {
  directory = await mkdtemp(join(tmpdir(), 'carelink-provider-build-'))
  vi.stubEnv('VITE_OPEN_PLATFORM_MOCK', '')
})

afterEach(async () => {
  vi.unstubAllEnvs()
  await rm(directory, { recursive: true, force: true })
})

function fixtureBuild(entry: string) {
  return build({
    ...config,
    configFile: false,
    root: fileURLToPath(new URL('.', import.meta.url)),
    mode: 'production',
    logLevel: 'silent',
    build: { lib: { entry, formats: ['es'] }, outDir: join(directory, 'dist'), emptyOutDir: true },
  })
}

it('refuses a publishable build with the local mock flag enabled', async () => {
  const entry = join(directory, 'empty.ts')
  await writeFile(entry, 'export const example = 1')
  vi.stubEnv('VITE_OPEN_PLATFORM_MOCK', 'true')

  await expect(fixtureBuild(entry)).rejects.toThrow('Provider mock mode is for local development and tests')
})

it('refuses a bundle that accidentally imports and uses the mock platform', async () => {
  const entry = join(directory, 'mock.ts')
  const mock = fileURLToPath(new URL('./src/features/provider-platform/mockPlatform.ts', import.meta.url))
  await writeFile(entry, `export { resetMockPlatform } from ${JSON.stringify(mock)}`)

  await expect(fixtureBuild(entry)).rejects.toThrow('Publishable builds must not contain the provider mock platform')
})
