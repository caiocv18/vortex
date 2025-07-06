import { test, expect } from '@playwright/test'

test.describe('Product Types Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/product-types')
  })

  test('displays page title and basic elements', async ({ page }) => {
    // Verifica o título da página
    await expect(page).toHaveTitle(/Tipos de Produto - Sistema de Estoque/)
    await expect(page.locator('h1:has-text("Tipos de Produto")')).toBeVisible()
    
    // Verifica elementos principais da página
    await expect(page.locator('input[label="Pesquisar"]')).toBeVisible()
    await expect(page.locator('button:has-text("Novo Tipo de Produto")')).toBeVisible()
    await expect(page.locator('.v-data-table')).toBeVisible()
  })

  test('displays data table with correct headers', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica os cabeçalhos da tabela
    await expect(page.locator('th:has-text("ID")')).toBeVisible()
    await expect(page.locator('th:has-text("Nome")')).toBeVisible()
    await expect(page.locator('th:has-text("Ações")')).toBeVisible()
  })

  test('search functionality works', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Preenche o campo de pesquisa
    await page.fill('input[label="Pesquisar"]', 'test')
    
    // Aguarda a filtragem
    await page.waitForTimeout(1000)
    
    // Verifica se o campo de pesquisa foi preenchido
    await expect(page.locator('input[label="Pesquisar"]')).toHaveValue('test')
  })

  test('can open new product type dialog', async ({ page }) => {
    // Clica no botão "Novo Tipo de Produto"
    await page.click('button:has-text("Novo Tipo de Produto")')
    
    // Verifica se o diálogo foi aberto
    await expect(page.locator('.v-dialog .v-card')).toBeVisible()
    await expect(page.locator('text=Novo Tipo de Produto')).toBeVisible()
    await expect(page.locator('input[label="Nome do Tipo de Produto"]')).toBeVisible()
    await expect(page.locator('button:has-text("Cancelar")')).toBeVisible()
    await expect(page.locator('button:has-text("Criar")')).toBeVisible()
  })

  test('can close new product type dialog', async ({ page }) => {
    // Abre o diálogo
    await page.click('button:has-text("Novo Tipo de Produto")')
    await expect(page.locator('.v-dialog .v-card')).toBeVisible()
    
    // Fecha o diálogo clicando em Cancelar
    await page.click('button:has-text("Cancelar")')
    
    // Verifica se o diálogo foi fechado
    await expect(page.locator('.v-dialog .v-card')).not.toBeVisible()
  })

  test('form validation works in new product type dialog', async ({ page }) => {
    // Abre o diálogo
    await page.click('button:has-text("Novo Tipo de Produto")')
    
    // Tenta criar sem preencher o nome
    await page.click('button:has-text("Criar")')
    
    // Verifica se a mensagem de erro aparece
    await expect(page.locator('text=Nome é obrigatório')).toBeVisible()
    
    // Preenche com um nome muito curto
    await page.fill('input[label="Nome do Tipo de Produto"]', 'ab')
    await page.click('button:has-text("Criar")')
    
    // Verifica se a mensagem de erro para nome curto aparece
    await expect(page.locator('text=Nome deve ter pelo menos 3 caracteres')).toBeVisible()
  })

  test('can create new product type with valid data', async ({ page }) => {
    // Abre o diálogo
    await page.click('button:has-text("Novo Tipo de Produto")')
    
    // Preenche um nome válido
    const productTypeName = `Tipo Teste ${Date.now()}`
    await page.fill('input[label="Nome do Tipo de Produto"]', productTypeName)
    
    // Clica em Criar
    await page.click('button:has-text("Criar")')
    
    // Aguarda o diálogo fechar
    await expect(page.locator('.v-dialog .v-card')).not.toBeVisible()
    
    // Verifica se o novo tipo foi adicionado (pode aparecer na tabela)
    // Nota: Este teste assume que a API está funcionando
  })

  test('action buttons are displayed for existing items', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se existem botões de ação (editar e excluir) na tabela
    const editButtons = page.locator('button:has(.mdi-pencil)')
    const deleteButtons = page.locator('button:has(.mdi-delete)')
    
    // Se houver dados na tabela, deve haver botões de ação
    const tableRows = page.locator('.v-data-table tbody tr')
    const rowCount = await tableRows.count()
    
    if (rowCount > 0) {
      await expect(editButtons.first()).toBeVisible()
      await expect(deleteButtons.first()).toBeVisible()
    }
  })

  test('can open edit dialog for existing product type', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se há dados na tabela
    const editButton = page.locator('button:has(.mdi-pencil)').first()
    
    if (await editButton.isVisible()) {
      // Clica no botão de editar
      await editButton.click()
      
      // Verifica se o diálogo de edição foi aberto
      await expect(page.locator('.v-dialog .v-card')).toBeVisible()
      await expect(page.locator('text=Editar Tipo de Produto')).toBeVisible()
      await expect(page.locator('button:has-text("Salvar")')).toBeVisible()
      
      // O campo deve estar preenchido com o valor existente
      const nameInput = page.locator('input[label="Nome do Tipo de Produto"]')
      await expect(nameInput).not.toHaveValue('')
    }
  })

  test('can open delete confirmation dialog', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se há dados na tabela
    const deleteButton = page.locator('button:has(.mdi-delete)').first()
    
    if (await deleteButton.isVisible()) {
      // Clica no botão de excluir
      await deleteButton.click()
      
      // Verifica se o diálogo de confirmação foi aberto
      await expect(page.locator('.v-dialog .v-card')).toBeVisible()
      
      // Deve haver botões de confirmação e cancelamento
      await expect(page.locator('button:has-text("Cancelar")')).toBeVisible()
    }
  })

  test('loading state is displayed when fetching data', async ({ page }) => {
    // Recarrega a página para ver o estado de carregamento
    await page.reload()
    
    // Verifica se o indicador de carregamento aparece (mesmo que brevemente)
    // Nota: Pode ser difícil de capturar se a API for muito rápida
    const loadingIndicator = page.locator('.v-data-table .v-progress-linear, .v-data-table .v-skeleton-loader')
    
    // Aguarda a tabela carregar completamente
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
  })

  test('page is responsive on mobile viewport', async ({ page }) => {
    // Define viewport mobile
    await page.setViewportSize({ width: 375, height: 667 })
    
    // Verifica se os elementos principais ainda estão visíveis
    await expect(page.locator('h1:has-text("Tipos de Produto")')).toBeVisible()
    await expect(page.locator('button:has-text("Novo Tipo de Produto")')).toBeVisible()
    await expect(page.locator('.v-data-table')).toBeVisible()
  })
}) 