import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { defineConfig, devices } from '@playwright/test'

/**
 * Aplicação sob teste.
 * - Padrão: http://localhost:5173. Se a porta já responde (dev server aberto, ou o modo leve do
 *   start-vortex.sh servindo o build), o Playwright reaproveita; senão sobe o dev server do Vite
 *   (no CI, o `vite preview` do build).
 * - PLAYWRIGHT_BASE_URL=<url>: testa uma aplicação que já está no ar e NÃO sobe servidor nenhum.
 *   Ex.: PLAYWRIGHT_BASE_URL=http://localhost:3000 testa o container do docker compose. Se ele não
 *   estiver de pé, o projeto setup falha no primeiro page.goto, em vez de a suíte cair calada num
 *   dev server aberto na mesma porta.
 */
const externalBaseURL = process.env.PLAYWRIGHT_BASE_URL
const LOCAL_PORT = 5173
const baseURL = externalBaseURL || `http://localhost:${LOCAL_PORT}`

/**
 * Sessão autenticada gravada pelo projeto "setup" (e2e/auth.setup.ts, mesmo caminho de
 * e2e/support/auth.ts). Todas as rotas exigem login, então os projetos de browser partem dela.
 * Fica fora do git (.gitignore).
 */
const authStateFile = fileURLToPath(new URL('./playwright/.auth/user.json', import.meta.url))

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  testDir: './e2e',
  /* Maximum time one test can run for. */
  timeout: 30 * 1000,
  expect: {
    /**
     * Maximum time expect() should wait for the condition to be met.
     * For example in `await expect(locator).toHaveText();`
     */
    timeout: 5000,
  },
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,
  /* Opt out of parallel tests on CI. */
  workers: process.env.CI ? 1 : undefined,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  /* open: 'never': no WSL o relatório não abre browser nem segura o terminal */
  reporter: [['html', { open: 'never' }]],
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Maximum time each action such as `click()` can take. Defaults to 0 (no limit). */
    actionTimeout: 0,
    /* Base URL to use in actions like `await page.goto('/')`. */
    baseURL,

    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',

    /* Headless por padrão (com janela, o WSL abre o WSLg). PLAYWRIGHT_HEADED=true para ver o browser. */
    headless: process.env.PLAYWRIGHT_HEADED !== 'true',
  },

  /* Configure projects for major browsers */
  projects: [
    /* Cadastra e autentica um usuário pela API do auth e grava o storageState. */
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts$/,
    },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        storageState: authStateFile,
      },
      dependencies: ['setup'],
    },
    {
      name: 'firefox',
      use: {
        ...devices['Desktop Firefox'],
        storageState: authStateFile,
      },
      dependencies: ['setup'],
    },
    {
      name: 'webkit',
      use: {
        ...devices['Desktop Safari'],
        storageState: authStateFile,
      },
      dependencies: ['setup'],
    },

    /* Test against mobile viewports. */
    // {
    //   name: 'Mobile Chrome',
    //   use: {
    //     ...devices['Pixel 5'],
    //   },
    // },
    // {
    //   name: 'Mobile Safari',
    //   use: {
    //     ...devices['iPhone 12'],
    //   },
    // },

    /* Test against branded browsers. */
    // {
    //   name: 'Microsoft Edge',
    //   use: {
    //     channel: 'msedge',
    //   },
    // },
    // {
    //   name: 'Google Chrome',
    //   use: {
    //     channel: 'chrome',
    //   },
    // },
  ],

  /* Folder for test artifacts such as screenshots, videos, traces, etc. */
  // outputDir: 'test-results/',

  /* Run your local dev server before starting the tests (só no alvo local; ver baseURL acima) */
  ...(externalBaseURL
    ? {}
    : {
        webServer: {
          /**
           * Use the dev server by default for faster feedback loop.
           * Use the preview server on CI for more realistic testing.
           * Os dois sobem na porta local fixa, então definir CI não muda a porta.
           * Playwright will re-use the local server if there is already a dev-server running.
           */
          command: process.env.CI
            ? `npm run preview -- --port ${LOCAL_PORT} --strictPort`
            : `npm run dev -- --port ${LOCAL_PORT} --strictPort`,
          url: baseURL,
          reuseExistingServer: !process.env.CI,
        },
      }),
})
