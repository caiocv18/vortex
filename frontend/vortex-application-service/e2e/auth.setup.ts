import fs from 'node:fs'
import path from 'node:path'
import { test as setup, expect } from '@playwright/test'
import { AUTH_API_URL, AUTH_STATE_FILE, AUTH_STORAGE_KEYS } from './support/auth'

// Projeto "setup" (ver playwright.config.ts): roda uma vez antes dos projetos de browser.
// Cadastra um usuário novo pela API do serviço de autorização, faz login e grava um
// storageState com exatamente as chaves de localStorage que o app lê (as mesmas que
// src/utils/authCallback.ts grava quando o app de login devolve ?authData=...).

interface AuthPayload {
  accessToken: string
  refreshToken: string
  user: Record<string, unknown>
}

interface AuthEnvelope {
  success: boolean
  message?: string
  data?: AuthPayload
}

setup('cadastra, autentica e grava a sessão do usuário de teste', async ({
  request,
  browser,
  baseURL,
}) => {
  expect(baseURL, 'baseURL precisa estar definida no playwright.config.ts').toBeTruthy()
  const appOrigin = new URL(baseURL!).origin

  // Usuário único por execução: o H2 do app zera a cada subida, o Postgres do auth não
  const id = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
  const username = `e2e_${id}`
  const email = `${username}@vortex.test`
  // Política de senha: maiúscula, minúscula, dígito, especial e 8 ou mais caracteres
  const password = `E2e!${id}Aa1`

  const register = await request.post(`${AUTH_API_URL}/api/auth/register`, {
    data: { email, username, password, confirmPassword: password },
  })
  expect(register.status(), `register: ${await register.text()}`).toBe(201)

  // Login de verdade (o register também devolve tokens, mas o fluxo do usuário é o login)
  const login = await request.post(`${AUTH_API_URL}/api/auth/login`, {
    data: { identifier: email, password },
  })
  expect(login.status(), `login: ${await login.text()}`).toBe(200)
  const body = (await login.json()) as AuthEnvelope
  expect(body.success).toBe(true)
  const auth = body.data
  expect(auth?.accessToken).toBeTruthy()
  expect(auth?.refreshToken).toBeTruthy()
  expect(auth?.user).toMatchObject({ email, username })

  const state = {
    cookies: [],
    origins: [
      {
        origin: appOrigin,
        localStorage: [
          { name: AUTH_STORAGE_KEYS.accessToken, value: auth!.accessToken },
          { name: AUTH_STORAGE_KEYS.refreshToken, value: auth!.refreshToken },
          { name: AUTH_STORAGE_KEYS.user, value: JSON.stringify(auth!.user) },
        ],
      },
    ],
  }
  fs.mkdirSync(path.dirname(AUTH_STATE_FILE), { recursive: true })
  fs.writeFileSync(AUTH_STATE_FILE, JSON.stringify(state, null, 2))

  // Prova que o estado basta: com ele o guard deixa entrar e o app mostra o shell logado
  const context = await browser.newContext({ baseURL, storageState: AUTH_STATE_FILE })
  try {
    const page = await context.newPage()
    await page.goto('/')
    await expect(page).toHaveURL(`${appOrigin}/`)
    await expect(page.getByRole('navigation')).toBeVisible()
    await expect(page.getByRole('banner').getByText('Sistema de Estoque')).toBeVisible()
  } finally {
    await context.close()
  }
})
