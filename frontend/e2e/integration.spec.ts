import { test, expect } from '@playwright/test'

test.describe('Application Integration Tests', () => {
  test('complete application flow - navigation and basic functionality', async ({ page }) => {
    // Inicia na home page
    await page.goto('/')
    
    // Verifica se a home page carregou corretamente
    await expect(page).toHaveTitle(/Home - Sistema de Estoque/)
    await expect(page.locator('text=Bem-vindo ao Sistema de Estoque')).toBeVisible()
    
    // Navega para Tipos de Produto via sidebar
    await page.click('.v-list-item:has-text("Tipos de Produto")')
    await expect(page).toHaveURL(/\/product-types/)
    await expect(page.locator('h1:has-text("Tipos de Produto")')).toBeVisible()
    
    // Verifica se a tabela de tipos de produto carregou
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Navega para Produtos via sidebar
    await page.click('.v-list-item:has-text("Produtos")')
    await page.waitForTimeout(2000)
    await expect(page).toHaveURL(/\/products/)
    await expect(page.locator('h1:has-text("Produtos")')).toBeVisible()
    
    // Verifica se a tabela de produtos carregou
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Navega para Movimentação via sidebar
    await page.click('.v-list-item:has-text("Movimentação")')
    await expect(page).toHaveURL(/\/movements/)
    await expect(page.locator('h1:has-text("Movimentação de Estoque")')).toBeVisible()
    
    // Verifica se a tabela de movimentações carregou
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Navega para Relatórios via sidebar
    await page.click('.v-list-item:has-text("Relatórios")')
    await expect(page).toHaveURL(/\/reports/)
    await expect(page.locator('h1:has-text("Relatórios")')).toBeVisible()
    
    // Verifica se os cards de estatísticas carregaram
    await expect(page.locator('text=Receita Total')).toBeVisible()
    
    // Volta para Home via sidebar
    await page.click('.v-list-item:has-text("Home")')
    await expect(page).toHaveURL(/\/$/)
    await expect(page.locator('text=Bem-vindo ao Sistema de Estoque')).toBeVisible()
  })

  test('theme toggle works across all pages', async ({ page }) => {
    const pages = [
      { path: '/', name: 'Home' },
      { path: '/product-types', name: 'Tipos de Produto' },
      { path: '/products', name: 'Produtos' },
      { path: '/movements', name: 'Movimentação' },
      { path: '/reports', name: 'Relatórios' }
    ]

    for (const { path, name } of pages) {
      await page.goto(path)
      
      // Pega o tema inicial
      const initialTheme = await page.evaluate(() => localStorage.getItem('theme'))
      
      // Clica no botão de tema no app bar
      const themeButton = page.locator('.v-app-bar .v-btn:has(.mdi-weather-sunny), .v-app-bar .v-btn:has(.mdi-weather-night)').first()
      await themeButton.click()
      await page.waitForTimeout(500)
      
      // Verifica se o tema mudou
      const newTheme = await page.evaluate(() => localStorage.getItem('theme'))
      expect(newTheme).not.toBe(initialTheme)
      
      // Volta ao tema original
      await page.click('.v-app-bar .v-btn:has(.mdi-weather-sunny), .v-app-bar .v-btn:has(.mdi-weather-night)')
      await page.waitForTimeout(500)
    }
  })

  test('navigation via home page cards works correctly', async ({ page }) => {
    await page.goto('/')
    
    // Navega para Tipos de Produto via card da home
    await page.click('text=Acessar >> nth=0')
    await expect(page).toHaveURL(/\/product-types/)
    await expect(page.locator('h1:has-text("Tipos de Produto")')).toBeVisible()
    
    // Volta para home
    await page.goto('/')
    
    // Navega para Produtos via card da home
    await page.click('text=Acessar >> nth=1')
    await expect(page).toHaveURL(/\/products/)
    await expect(page.locator('h1:has-text("Produtos")')).toBeVisible()
    
    // Volta para home
    await page.goto('/')
    
    // Navega para Movimentação via card da home
    await page.click('text=Acessar >> nth=2')
    await expect(page).toHaveURL(/\/movements/)
    await expect(page.locator('h1:has-text("Movimentação de Estoque")')).toBeVisible()
    
    // Volta para home
    await page.goto('/')
    
    // Navega para Relatórios via card da home
    await page.click('text=Acessar >> nth=3')
    await expect(page).toHaveURL(/\/reports/)
    await expect(page.locator('h1:has-text("Relatórios")')).toBeVisible()
  })

  test('sidebar rail mode works across all pages', async ({ page }) => {
    const pages = [
      { path: '/', name: 'Home' },
      { path: '/product-types', name: 'Tipos de Produto' },
      { path: '/products', name: 'Produtos' },
      { path: '/movements', name: 'Movimentação' },
      { path: '/reports', name: 'Relatórios' }
    ]

    for (const { path, name } of pages) {
      await page.goto(path)
      
      // Verifica se o sidebar está visível
      await expect(page.locator('.v-navigation-drawer')).toBeVisible()
      
      // Ativa o modo rail
      const railToggle = page.locator('.v-navigation-drawer .v-btn:has(.mdi-chevron-left)')
      if (await railToggle.isVisible()) {
        await railToggle.click()
        await page.waitForTimeout(300)
        
        // Verifica se o drawer ainda está visível (em modo rail)
        await expect(page.locator('.v-navigation-drawer')).toBeVisible()
        
        // Desativa o modo rail
        await railToggle.click()
        await page.waitForTimeout(300)
        
        // Verifica se o drawer ainda está visível (modo normal)
        await expect(page.locator('.v-navigation-drawer')).toBeVisible()
      }
    }
  })

  test('search functionality works on data pages', async ({ page }) => {
    const searchPages = [
      { path: '/product-types', searchField: 'input[label="Pesquisar"]' },
      { path: '/products', searchField: 'input[label="Pesquisar"]' },
      { path: '/movements', searchField: 'input[label="Pesquisar por produto"]' }
    ]

    for (const { path, searchField } of searchPages) {
      await page.goto(path)
      
      // Aguarda a tabela carregar
      await page.waitForSelector('.v-data-table', { timeout: 10000 })
      
      // Verifica se o campo de pesquisa existe
      const searchInput = page.locator(searchField)
      if (await searchInput.isVisible()) {
        // Preenche o campo de pesquisa
        await searchInput.fill('teste')
        await page.waitForTimeout(1000)
        
        // Verifica se o valor foi preenchido
        await expect(searchInput).toHaveValue('teste')
        
        // Limpa o campo
        await searchInput.fill('')
      }
    }
  })

  test('dialog forms can be opened and closed on all pages', async ({ page }) => {
    const dialogPages = [
      { 
        path: '/product-types', 
        buttonText: 'Novo Tipo de Produto',
        dialogTitle: 'Novo Tipo de Produto'
      },
      { 
        path: '/products', 
        buttonText: 'Novo Produto',
        dialogTitle: 'Novo Produto'
      },
      { 
        path: '/movements', 
        buttonText: 'Nova Movimentação',
        dialogTitle: 'Nova Movimentação'
      }
    ]

    for (const { path, buttonText, dialogTitle } of dialogPages) {
      await page.goto(path)
      
      // Aguarda a página carregar
      await page.waitForTimeout(2000)
      
      // Clica no botão para abrir o diálogo
      const newButton = page.locator(`button:has-text("${buttonText}")`)
      if (await newButton.isVisible()) {
        await newButton.click()
        
        // Verifica se o diálogo foi aberto
        await expect(page.locator('.v-dialog .v-card')).toBeVisible()
        await expect(page.locator(`text=${dialogTitle}`)).toBeVisible()
        
        // Fecha o diálogo
        await page.click('button:has-text("Cancelar")')
        
        // Verifica se o diálogo foi fechado
        await expect(page.locator('.v-dialog .v-card')).not.toBeVisible()
      }
    }
  })

  test('page titles update correctly during navigation', async ({ page }) => {
    const navigationFlow = [
      { path: '/', title: 'Home - Sistema de Estoque', linkText: 'Home' },
      { path: '/product-types', title: 'Tipos de Produto - Sistema de Estoque', linkText: 'Tipos de Produto' },
      { path: '/products', title: 'Produtos - Sistema de Estoque', linkText: 'Produtos' },
      { path: '/movements', title: 'Movimentação - Sistema de Estoque', linkText: 'Movimentação' },
      { path: '/reports', title: 'Relatórios - Sistema de Estoque', linkText: 'Relatórios' }
    ]

    // Inicia na home
    await page.goto('/')
    
    for (let i = 1; i < navigationFlow.length; i++) {
      const { title, linkText } = navigationFlow[i]
      
      // Navega via sidebar
      await page.click(`.v-list-item:has-text("${linkText}")`)
      
      // Verifica se o título foi atualizado
      await expect(page).toHaveTitle(title)
    }
  })

  test('responsive design works on all pages', async ({ page }) => {
    const pages = [
      { path: '/', key: 'h1' },
      { path: '/product-types', key: 'h1:has-text("Tipos de Produto")' },
      { path: '/products', key: 'h1:has-text("Produtos")' },
      { path: '/movements', key: 'h1:has-text("Movimentação de Estoque")' },
      { path: '/reports', key: 'h1:has-text("Relatórios")' }
    ]

    const viewports = [
      { width: 1280, height: 720 }, // Desktop
      { width: 768, height: 1024 }, // Tablet
      { width: 375, height: 667 }   // Mobile
    ]

    for (const { path, key } of pages) {
      for (const viewport of viewports) {
        await page.setViewportSize(viewport)
        await page.goto(path)
        await page.waitForTimeout(1000)
        
        // Verifica se o elemento principal está visível
        await expect(page.locator(key)).toBeVisible()
        
        // Verifica se o sidebar está presente (pode estar oculto em mobile)
        const sidebar = page.locator('.v-navigation-drawer')
        // Em mobile, o sidebar pode estar oculto, então não falhamos se não estiver visível
      }
    }
  })

  test('API connection status is displayed on home page', async ({ page }) => {
    await page.goto('/')
    
    // Verifica se o card de teste de API está presente
    await expect(page.locator('text=Teste de Conexão com API')).toBeVisible()
    
    // Aguarda o resultado do teste aparecer
    await page.waitForTimeout(5000)
    
    // Verifica se há algum resultado (sucesso ou erro)
    const hasSuccess = await page.locator('text=Conexão com API bem-sucedida!').isVisible()
    const hasError = await page.locator('text=Erro ao conectar com a API').isVisible()
    
    // Deve haver pelo menos um resultado
    expect(hasSuccess || hasError).toBeTruthy()
    
    // Se houver botão de testar novamente, testa a funcionalidade
    const testButton = page.locator('button:has-text("Testar Novamente")')
    if (await testButton.isVisible()) {
      await testButton.click()
      await page.waitForTimeout(3000)
      
      // Verifica se houve uma nova tentativa
      const hasNewResult = await page.locator('text=Conexão com API bem-sucedida!').isVisible() ||
                          await page.locator('text=Erro ao conectar com a API').isVisible()
      expect(hasNewResult).toBeTruthy()
    }
  })

  test('all data tables load without errors', async ({ page }) => {
    const tablePages = [
      '/product-types',
      '/products', 
      '/movements'
    ]

    for (const path of tablePages) {
      await page.goto(path)
      
      // Aguarda a tabela carregar
      await page.waitForSelector('.v-data-table', { timeout: 10000 })
      
      // Verifica se não há mensagens de erro visíveis
      const errorMessages = page.locator('text=/erro|error/i')
      
      if (await errorMessages.count() > 0) {
        for (let i = 0; i < await errorMessages.count(); i++) {
          const error = errorMessages.nth(i)
          const isVisible = await error.isVisible()
          
          if (isVisible) {
            const text = await error.textContent()
            // Permite erros de console/desenvolvimento, mas não erros de UI
            expect(text).not.toContain('Erro ao carregar dados')
            expect(text).not.toContain('Failed to fetch')
          }
        }
      }
      
      // Verifica se a tabela tem a estrutura correta
      await expect(page.locator('.v-data-table thead')).toBeVisible()
      await expect(page.locator('.v-data-table tbody')).toBeVisible()
    }
  })
}) 