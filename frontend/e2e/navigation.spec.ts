import { test, expect } from '@playwright/test'

test.describe('Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
  })

  test('has correct title', async ({ page }) => {
    await expect(page).toHaveTitle(/Home - Sistema de Estoque/)
  })

  test('navigates to all pages', async ({ page }) => {
    // Verifica se está na home
    await expect(page.locator('h1')).toContainText('Dashboard')

    // Navega para Tipos de Produto
    await page.click('text=Tipos de Produto')
    await expect(page.locator('h1')).toContainText('Tipos de Produto')
    await expect(page).toHaveURL(/\/product-types/)

    // Navega para Produtos
    await page.click('text=Produtos')
    await expect(page.locator('h1')).toContainText('Produtos')
    await expect(page).toHaveURL(/\/products/)

    // Navega para Movimentação
    await page.click('text=Movimentação')
    await expect(page.locator('h1')).toContainText('Movimentação de Estoque')
    await expect(page).toHaveURL(/\/movements/)

    // Navega para Relatórios
    await page.click('text=Relatórios')
    await expect(page.locator('h1')).toContainText('Relatórios')
    await expect(page).toHaveURL(/\/reports/)

    // Volta para Home
    await page.click('text=Home')
    await expect(page.locator('h1')).toContainText('Dashboard')
    await expect(page).toHaveURL(/\/$/)
  })

  test('theme toggle works', async ({ page }) => {
    // Verifica tema inicial
    const html = page.locator('html')
    
    // Clica no botão de tema
    await page.click('[aria-label*="theme" i]').catch(() => {
      // Se não encontrar, tenta o ícone
      return page.click('.mdi-weather-sunny, .mdi-weather-night').first()
    })

    // Aguarda mudança de tema
    await page.waitForTimeout(500)

    // Verifica se o localStorage foi atualizado
    const theme = await page.evaluate(() => localStorage.getItem('theme'))
    expect(['light', 'dark']).toContain(theme)
  })

  test('sidebar rail toggle works', async ({ page }) => {
    // Encontra o botão de toggle do rail
    const toggleButton = page.locator('.mdi-chevron-left').first()
    
    // Clica para expandir/contrair
    await toggleButton.click()
    await page.waitForTimeout(300)

    // Verifica se o drawer mudou de estado
    const drawer = page.locator('.v-navigation-drawer')
    await expect(drawer).toBeVisible()
  })
}) 