// @vitest-environment node
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, it, expect, afterEach, vi } from 'vitest'

const projectRoot = fileURLToPath(new URL('../../', import.meta.url))
const readJson = <T>(relativePath: string): T =>
  JSON.parse(readFileSync(path.join(projectRoot, relativePath), 'utf8')) as T

describe('npm scripts', () => {
  const { scripts } = readJson<{ scripts: Record<string, string> }>('package.json')

  // No Windows o npm roda os scripts no cmd.exe, que não entende `VAR=valor comando`
  // ("'NODE_OPTIONS' is not recognized..."): o build parava no type-check.
  const POSIX_INLINE_ENV = /(?:^|&&|\|\||[;|])\s*[A-Za-z_][A-Za-z0-9_]*=/

  it.each(Object.entries(scripts))('%s has no POSIX inline env assignment', (_name, command) => {
    expect(command).not.toMatch(POSIX_INLINE_ENV)
  })

  it('type-check caps the heap through a node flag on the real vue-tsc bin', () => {
    const typeCheck = scripts['type-check']!
    expect(typeCheck).toMatch(/^node --max-old-space-size=1536 \S+ --build$/)

    const binPath = typeCheck.split(' ')[2]!
    const vueTsc = readJson<{ bin: Record<string, string> }>('node_modules/vue-tsc/package.json')
    expect(path.normalize(binPath)).toBe(
      path.normalize(path.join('node_modules/vue-tsc', vueTsc.bin['vue-tsc']!)),
    )
    expect(existsSync(path.join(projectRoot, binPath))).toBe(true)
  })
})

describe('playwright.config.ts target', () => {
  async function loadConfig(env: { PLAYWRIGHT_BASE_URL: string; CI: string }) {
    vi.resetModules()
    vi.stubEnv('PLAYWRIGHT_BASE_URL', env.PLAYWRIGHT_BASE_URL)
    vi.stubEnv('CI', env.CI)
    return (await import('../../playwright.config')).default
  }

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('tests an external URL as it is, without starting any server', async () => {
    const config = await loadConfig({ PLAYWRIGHT_BASE_URL: 'http://localhost:3000', CI: '' })

    expect(config.use?.baseURL).toBe('http://localhost:3000')
    expect(config.webServer).toBeUndefined()
  })

  it('stays without a server for an external URL on CI too', async () => {
    const config = await loadConfig({ PLAYWRIGHT_BASE_URL: 'https://vortex.example', CI: 'true' })

    expect(config.use?.baseURL).toBe('https://vortex.example')
    expect(config.webServer).toBeUndefined()
  })

  it('reuses or starts the Vite dev server on 5173 by default', async () => {
    const config = await loadConfig({ PLAYWRIGHT_BASE_URL: '', CI: '' })

    expect(config.use?.baseURL).toBe('http://localhost:5173')
    expect(config.webServer).toEqual({
      command: 'npm run dev -- --port 5173 --strictPort',
      url: 'http://localhost:5173',
      reuseExistingServer: true,
    })
  })

  it('starts vite preview on the same port on CI', async () => {
    const config = await loadConfig({ PLAYWRIGHT_BASE_URL: '', CI: 'true' })

    expect(config.webServer).toEqual({
      command: 'npm run preview -- --port 5173 --strictPort',
      url: 'http://localhost:5173',
      reuseExistingServer: false,
    })
  })
})
