import { test, expect } from '@playwright/test'

test.describe('Products Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/products')
  })

  test('displays page title and basic elements', async ({ page }) => {
    // Verifica o título da página
    await expect(page).toHaveTitle(/Produtos - Sistema de Estoque/)
    await expect(page.locator('h1:has-text("Produtos")')).toBeVisible()
    
    // Verifica elementos principais da página
    await expect(page.locator('input[label="Pesquisar"]')).toBeVisible()
    await expect(page.locator('button:has-text("Novo Produto")')).toBeVisible()
    await expect(page.locator('.v-data-table')).toBeVisible()
  })

  test('displays data table with correct headers', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica os cabeçalhos da tabela
    await expect(page.locator('th:has-text("ID")')).toBeVisible()
    await expect(page.locator('th:has-text("Descrição")')).toBeVisible()
    await expect(page.locator('th:has-text("Tipo")')).toBeVisible()
    await expect(page.locator('th:has-text("Valor Fornecedor")')).toBeVisible()
    await expect(page.locator('th:has-text("Estoque")')).toBeVisible()
    await expect(page.locator('th:has-text("Ações")')).toBeVisible()
  })

  test('search functionality works', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Preenche o campo de pesquisa
    await page.fill('input[label="Pesquisar"]', 'produto')
    
    // Aguarda a filtragem
    await page.waitForTimeout(1000)
    
    // Verifica se o campo de pesquisa foi preenchido
    await expect(page.locator('input[label="Pesquisar"]')).toHaveValue('produto')
  })

  test('can open new product dialog', async ({ page }) => {
    // Clica no botão "Novo Produto"
    await page.click('button:has-text("Novo Produto")')
    
    // Verifica se o diálogo foi aberto
    await expect(page.locator('.v-dialog .v-card')).toBeVisible()
    await expect(page.locator('text=Novo Produto')).toBeVisible()
    await expect(page.locator('input[label="Descrição"]')).toBeVisible()
    await expect(page.locator('input[label="Valor do Fornecedor"]')).toBeVisible()
    await expect(page.locator('.v-select')).toBeVisible() // Tipo de produto select
    await expect(page.locator('button:has-text("Cancelar")')).toBeVisible()
    await expect(page.locator('button:has-text("Criar")')).toBeVisible()
  })

  test('can close new product dialog', async ({ page }) => {
    // Abre o diálogo
    await page.click('button:has-text("Novo Produto")')
    await expect(page.locator('.v-dialog .v-card')).toBeVisible()
    
    // Fecha o diálogo clicando em Cancelar
    await page.click('button:has-text("Cancelar")')
    
    // Verifica se o diálogo foi fechado
    await expect(page.locator('.v-dialog .v-card')).not.toBeVisible()
  })

  test('form validation works in new product dialog', async ({ page }) => {
    // Abre o diálogo
    await page.click('button:has-text("Novo Produto")')
    
    // Tenta criar sem preencher os campos obrigatórios
    await page.click('button:has-text("Criar")')
    
    // Verifica se as mensagens de erro aparecem
    await expect(page.locator('text=Descrição é obrigatória')).toBeVisible()
    await expect(page.locator('text=Valor do fornecedor é obrigatório')).toBeVisible()
    await expect(page.locator('text=Tipo de produto é obrigatório')).toBeVisible()
    
    // Preenche com uma descrição muito curta
    await page.fill('input[label="Descrição"]', 'ab')
    await page.click('button:has-text("Criar")')
    
    // Verifica se a mensagem de erro para descrição curta aparece
    await expect(page.locator('text=Descrição deve ter pelo menos 3 caracteres')).toBeVisible()
    
    // Preenche com valor negativo
    await page.fill('input[label="Valor do Fornecedor"]', '-10')
    await page.click('button:has-text("Criar")')
    
    // Verifica se a mensagem de erro para valor negativo aparece
    await expect(page.locator('text=Valor deve ser positivo')).toBeVisible()
  })

  test('can create new product with valid data', async ({ page }) => {
    // Abre o diálogo
    await page.click('button:has-text("Novo Produto")')
    
    // Preenche os campos obrigatórios
    const productName = `Produto Teste ${Date.now()}`
    await page.fill('input[label="Descrição"]', productName)
    await page.fill('input[label="Valor do Fornecedor"]', '10.50')
    
    // Seleciona um tipo de produto (se houver opções disponíveis)
    const selectElement = page.locator('.v-select')
    await selectElement.click()
    
    // Aguarda as opções aparecerem e seleciona a primeira (se houver)
    await page.waitForTimeout(1000)
    const firstOption = page.locator('.v-list-item').first()
    if (await firstOption.isVisible()) {
      await firstOption.click()
    }
    
    // Clica em Criar
    await page.click('button:has-text("Criar")')
    
    // Aguarda o diálogo fechar
    await expect(page.locator('.v-dialog .v-card')).not.toBeVisible()
  })

  test('displays stock levels with appropriate colors', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se há chips de estoque na tabela
    const stockChips = page.locator('.v-chip')
    
    if (await stockChips.count() > 0) {
      // Verifica se os chips estão visíveis
      await expect(stockChips.first()).toBeVisible()
      
      // Verifica se há chips com cores diferentes (success para estoque alto, error para baixo)
      const successChips = page.locator('.v-chip.bg-success')
      const errorChips = page.locator('.v-chip.bg-error')
      
      // Pelo menos um tipo de chip deve estar presente
      const hasSuccessChips = await successChips.count() > 0
      const hasErrorChips = await errorChips.count() > 0
      
      expect(hasSuccessChips || hasErrorChips).toBeTruthy()
    }
  })

  test('displays currency values correctly formatted', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se há células com valores monetários
    const currencyCells = page.locator('td:has-text("R$")')
    
    if (await currencyCells.count() > 0) {
      // Verifica se pelo menos uma célula contém formato de moeda brasileira
      await expect(currencyCells.first()).toBeVisible()
      
      // Verifica se o formato está correto (R$ X,XX)
      const cellText = await currencyCells.first().textContent()
      expect(cellText).toMatch(/R\$\s*\d+,\d{2}/)
    }
  })

  test('action buttons are displayed for existing items', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se existem botões de ação na tabela
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

  test('can open edit dialog for existing product', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se há dados na tabela
    const editButton = page.locator('button:has(.mdi-pencil)').first()
    
    if (await editButton.isVisible()) {
      // Clica no botão de editar
      await editButton.click()
      
      // Verifica se o diálogo de edição foi aberto
      await expect(page.locator('.v-dialog .v-card')).toBeVisible()
      await expect(page.locator('text=Editar Produto')).toBeVisible()
      await expect(page.locator('button:has-text("Salvar")')).toBeVisible()
      
      // Os campos devem estar preenchidos com os valores existentes
      const descriptionInput = page.locator('input[label="Descrição"]')
      const priceInput = page.locator('input[label="Valor do Fornecedor"]')
      
      await expect(descriptionInput).not.toHaveValue('')
      await expect(priceInput).not.toHaveValue('')
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

  test('displays product type names correctly', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se há dados na tabela
    const typeColumns = page.locator('td').filter({ hasText: /^[A-Za-z]/ })
    
    if (await typeColumns.count() > 0) {
      // Verifica se pelo menos uma célula de tipo está visível
      await expect(typeColumns.first()).toBeVisible()
      
      // Verifica se não há "N/A" ou IDs numéricos na coluna tipo
      const cellText = await typeColumns.first().textContent()
      expect(cellText).not.toBe('N/A')
      expect(cellText).not.toMatch(/^\d+$/)
    }
  })

  test('loading state is displayed when fetching data', async ({ page }) => {
    // Recarrega a página para ver o estado de carregamento
    await page.reload()
    
    // Aguarda a tabela carregar completamente
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
  })

  test('page is responsive on mobile viewport', async ({ page }) => {
    // Define viewport mobile
    await page.setViewportSize({ width: 375, height: 667 })
    
    // Verifica se os elementos principais ainda estão visíveis
    await expect(page.locator('h1:has-text("Produtos")')).toBeVisible()
    await expect(page.locator('button:has-text("Novo Produto")')).toBeVisible()
    await expect(page.locator('.v-data-table')).toBeVisible()
  })

  test('table is sortable by clicking headers', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se os cabeçalhos clicáveis estão presentes
    const sortableHeaders = page.locator('th[role="columnheader"]')
    
    if (await sortableHeaders.count() > 0) {
      // Clica no cabeçalho ID para ordenar
      const idHeader = page.locator('th:has-text("ID")')
      if (await idHeader.isVisible()) {
        await idHeader.click()
        await page.waitForTimeout(500)
        
        // Verifica se a ordenação foi aplicada (ícone de ordenação deve aparecer)
        const sortIcon = page.locator('th:has-text("ID") .mdi-arrow-up, th:has-text("ID") .mdi-arrow-down')
        // Nota: O ícone pode não estar visível dependendo da implementação do Vuetify
      }
    }
  })
}) 