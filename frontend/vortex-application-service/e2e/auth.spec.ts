import { test, expect } from '@playwright/test'
import { AUTH_APP_URL } from './support/auth'

const escapeRegExp = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

test.describe('Autenticação', () => {
  // Contexto sem sessão: ignora o storageState que o projeto recebe do setup
  test.use({ storageState: { cookies: [], origins: [] } })

  test('visita sem sessão redireciona para o app de login', async ({ page }) => {
    await page.goto('/products')

    // O guard (src/router/index.ts) cancela a navegação e manda para AUTH_FRONTEND_URL/login
    await expect(page).toHaveURL(new RegExp(`^${escapeRegExp(AUTH_APP_URL)}/login`))
    await expect(page.getByRole('heading', { name: 'Welcome Back' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Produtos' })).toHaveCount(0)
  })
})
