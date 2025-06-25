import { test, expect } from '@playwright/test'

test.describe('Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
  })

  test('has correct title on home page', async ({ page }) => {
    await expect(page).toHaveTitle(/Home - Sistema de Estoque/)
  })

  test('displays sidebar navigation with all menu items', async ({ page }) => {
    // Verifica se o sidebar está visível
    await expect(page.locator('.v-navigation-drawer')).toBeVisible()
    
    // Verifica o título do sistema no sidebar
    await expect(page.locator('text=Sistema de Estoque')).toBeVisible()
    await expect(page.locator('text=Nexdom')).toBeVisible()
    
    // Verifica todos os itens do menu
    await expect(page.locator('.v-list-item:has-text("Home")')).toBeVisible()
    await expect(page.locator('.v-list-item:has-text("Tipos de Produto")')).toBeVisible()
    await expect(page.locator('.v-list-item:has-text("Produtos")')).toBeVisible()
    await expect(page.locator('.v-list-item:has-text("Movimentação")')).toBeVisible()
    await expect(page.locator('.v-list-item:has-text("Relatórios")')).toBeVisible()
  })

  test('navigates to all pages via sidebar menu', async ({ page }) => {
    // Navega para Tipos de Produto
    await page.click('.v-list-item:has-text("Tipos de Produto")')
    await expect(page).toHaveURL(/\/product-types/)
    await expect(page.locator('h1:has-text("Tipos de Produto")')).toBeVisible()
    await expect(page).toHaveTitle(/Tipos de Produto - Sistema de Estoque/)

    // Navega para Produtos
    await page.click('.v-list-item:has-text("Produtos")')
    await expect(page).toHaveURL(/\/products/)
    await expect(page.locator('h1:has-text("Produtos")')).toBeVisible()
    await expect(page).toHaveTitle(/Produtos - Sistema de Estoque/)

    // Navega para Movimentação
    await page.click('.v-list-item:has-text("Movimentação")')
    await expect(page).toHaveURL(/\/movements/)
    await expect(page.locator('h1:has-text("Movimentação de Estoque")')).toBeVisible()
    await expect(page).toHaveTitle(/Movimentação - Sistema de Estoque/)

    // Navega para Relatórios
    await page.click('.v-list-item:has-text("Relatórios")')
    await expect(page).toHaveURL(/\/reports/)
    await expect(page.locator('h1:has-text("Relatórios")')).toBeVisible()
    await expect(page).toHaveTitle(/Relatórios - Sistema de Estoque/)

    // Volta para Home
    await page.click('.v-list-item:has-text("Home")')
    await expect(page).toHaveURL(/\/$/)
    await expect(page.locator('text=Bem-vindo ao Sistema de Estoque')).toBeVisible()
    await expect(page).toHaveTitle(/Home - Sistema de Estoque/)
  })

  test('app bar displays correct title and theme toggle', async ({ page }) => {
    // Verifica o título no app bar
    await expect(page.locator('.v-app-bar .v-toolbar-title:has-text("Sistema de Estoque")')).toBeVisible()
    
    // Verifica se o botão de tema está presente
    await expect(page.locator('.v-app-bar .v-btn .mdi-weather-sunny, .v-app-bar .v-btn .mdi-weather-night')).toBeVisible()
  })

  test('theme toggle works correctly', async ({ page }) => {
    // Pega o ícone inicial do tema
    const initialThemeIcon = await page.locator('.v-app-bar .v-btn .mdi-weather-sunny, .v-app-bar .v-btn .mdi-weather-night').first()
    const initialIconClass = await initialThemeIcon.getAttribute('class')
    
    // Clica no botão de tema no app bar
    await page.click('.v-app-bar .v-btn:has(.mdi-weather-sunny), .v-app-bar .v-btn:has(.mdi-weather-night)')
    
    // Aguarda a mudança de tema
    await page.waitForTimeout(500)
    
    // Verifica se o ícone mudou
    const newThemeIcon = await page.locator('.v-app-bar .v-btn .mdi-weather-sunny, .v-app-bar .v-btn .mdi-weather-night').first()
    const newIconClass = await newThemeIcon.getAttribute('class')
    
    expect(initialIconClass).not.toBe(newIconClass)
    
    // Verifica se o tema foi salvo no localStorage
    const theme = await page.evaluate(() => localStorage.getItem('theme'))
    expect(['light', 'dark']).toContain(theme)
  })

  test('sidebar theme toggle works correctly', async ({ page }) => {
    // Verifica se o item de tema está presente no sidebar
    const sidebarThemeItem = page.locator('.v-navigation-drawer .v-list-item:has(.mdi-weather-sunny), .v-navigation-drawer .v-list-item:has(.mdi-weather-night)')
    await expect(sidebarThemeItem).toBeVisible()
    
    // Clica no item de tema no sidebar
    await sidebarThemeItem.click()
    
    // Aguarda a mudança de tema
    await page.waitForTimeout(500)
    
    // Verifica se o tema foi salvo no localStorage
    const theme = await page.evaluate(() => localStorage.getItem('theme'))
    expect(['light', 'dark']).toContain(theme)
  })

  test('sidebar rail toggle works', async ({ page }) => {
    // Verifica se o botão de toggle do rail está presente
    const railToggleButton = page.locator('.v-navigation-drawer .v-btn:has(.mdi-chevron-left)')
    await expect(railToggleButton).toBeVisible()
    
    // Clica para ativar o modo rail
    await railToggleButton.click()
    await page.waitForTimeout(300)
    
    // Verifica se o drawer ainda está visível (modo rail)
    await expect(page.locator('.v-navigation-drawer')).toBeVisible()
    
    // Clica novamente para desativar o modo rail
    await railToggleButton.click()
    await page.waitForTimeout(300)
    
    // Verifica se o drawer ainda está visível (modo normal)
    await expect(page.locator('.v-navigation-drawer')).toBeVisible()
  })

  test('menu icons are displayed correctly', async ({ page }) => {
    // Verifica ícones dos itens do menu
    await expect(page.locator('.v-list-item:has-text("Home") .mdi-home')).toBeVisible()
    await expect(page.locator('.v-list-item:has-text("Tipos de Produto") .mdi-shape')).toBeVisible()
    await expect(page.locator('.v-list-item:has-text("Produtos") .mdi-package-variant')).toBeVisible()
    await expect(page.locator('.v-list-item:has-text("Movimentação") .mdi-swap-horizontal')).toBeVisible()
    await expect(page.locator('.v-list-item:has-text("Relatórios") .mdi-chart-box')).toBeVisible()
  })

  test('navigation drawer can be toggled via app bar button', async ({ page }) => {
    // Primeiro, ativa o modo rail para que o botão do app bar apareça
    await page.click('.v-navigation-drawer .v-btn:has(.mdi-chevron-left)')
    await page.waitForTimeout(300)
    
    // Verifica se o botão de menu do app bar está visível
    const appBarMenuButton = page.locator('.v-app-bar .v-app-bar-nav-icon')
    
    if (await appBarMenuButton.isVisible()) {
      // Clica no botão de menu do app bar
      await appBarMenuButton.click()
      await page.waitForTimeout(300)
      
      // Verifica se o drawer ainda está visível
      await expect(page.locator('.v-navigation-drawer')).toBeVisible()
    }
  })

  test('page titles update correctly when navigating', async ({ page }) => {
    const pages = [
      { path: '/product-types', title: 'Tipos de Produto - Sistema de Estoque' },
      { path: '/products', title: 'Produtos - Sistema de Estoque' },
      { path: '/movements', title: 'Movimentação - Sistema de Estoque' },
      { path: '/reports', title: 'Relatórios - Sistema de Estoque' },
      { path: '/', title: 'Home - Sistema de Estoque' }
    ]

    for (const { path, title } of pages) {
      await page.goto(path)
      await expect(page).toHaveTitle(title)
    }
  })
}) 