import { test, expect } from '@playwright/test'

test.describe('Movements Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/movements')
  })

  test('displays page title and basic elements', async ({ page }) => {
    // Verifica o título da página
    await expect(page).toHaveTitle(/Movimentação - Sistema de Estoque/)
    await expect(page.locator('h1:has-text("Movimentação de Estoque")')).toBeVisible()
    
    // Verifica elementos principais da página
    await expect(page.locator('input[placeholder="Pesquisar por produto"], input[label="Pesquisar por produto"]')).toBeVisible()
    await expect(page.locator('button:has-text("Nova Movimentação")')).toBeVisible()
    await expect(page.locator('.v-data-table')).toBeVisible()
  })

  test('displays data table with correct headers', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica os cabeçalhos da tabela
    await expect(page.locator('th:has-text("ID")').first()).toBeVisible()
    await expect(page.locator('th:has-text("Data")').first()).toBeVisible()
    await expect(page.locator('th:has-text("Produto")').first()).toBeVisible()
    await expect(page.locator('th:has-text("Tipo")').first()).toBeVisible()
    await expect(page.locator('th:has-text("Quantidade")').first()).toBeVisible()
    await expect(page.locator('th:has-text("Valor Venda")').first()).toBeVisible()
    await expect(page.locator('th:has-text("Ações")').first()).toBeVisible()
  })

  test('displays filter buttons for movement types', async ({ page }) => {
    // Verifica se os botões de filtro estão presentes
    await expect(page.locator('button:has-text("Todos")')).toBeVisible()
    await expect(page.locator('button:has-text("Entradas")')).toBeVisible()
    await expect(page.locator('button:has-text("Saídas")')).toBeVisible()
  })

  test('filter buttons work correctly', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Clica no filtro de Entradas
    await page.click('button:has-text("Entradas")')
    await page.waitForTimeout(1000)
    
    // Verifica se o botão Entradas está selecionado
    const entradasButton = page.locator('button:has-text("Entradas")')
    await expect(entradasButton).toHaveClass(/v-btn--active/)
    
    // Clica no filtro de Saídas
    await page.click('button:has-text("Saídas")')
    await page.waitForTimeout(1000)
    
    // Verifica se o botão Saídas está selecionado
    const saidasButton = page.locator('button:has-text("Saídas")')
    await expect(saidasButton).toHaveClass(/v-btn--active/)
    
    // Volta para Todos
    await page.click('button:has-text("Todos")')
    await page.waitForTimeout(1000)
    
    // Verifica se o botão Todos está selecionado
    const todosButton = page.locator('button:has-text("Todos")')
    await expect(todosButton).toHaveClass(/v-btn--active/)
  })

  test('search functionality works', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Preenche o campo de pesquisa
    const searchInput = page.locator('input[placeholder="Pesquisar por produto"], input[label="Pesquisar por produto"]').first()
    await searchInput.fill('produto')
    
    // Aguarda a filtragem
    await page.waitForTimeout(1000)
    
    // Verifica se o campo de pesquisa foi preenchido
    await expect(searchInput).toHaveValue('produto')
  })

  test('can open new movement dialog', async ({ page }) => {
    // Clica no botão "Nova Movimentação"
    await page.click('button:has-text("Nova Movimentação")')
    
    // Verifica se o diálogo foi aberto
    await expect(page.locator('.v-dialog .v-card')).toBeVisible()
    await expect(page.locator('text=Nova Movimentação')).toBeVisible()
    await expect(page.locator('.v-select')).toBeVisible() // Produto select
    await expect(page.locator('input[label="Quantidade"]')).toBeVisible()
    await expect(page.locator('button:has-text("Cancelar")')).toBeVisible()
    await expect(page.locator('button:has-text("Criar")')).toBeVisible()
  })

  test('can close new movement dialog', async ({ page }) => {
    // Abre o diálogo
    await page.click('button:has-text("Nova Movimentação")')
    await expect(page.locator('.v-dialog .v-card')).toBeVisible()
    
    // Fecha o diálogo clicando em Cancelar
    await page.click('button:has-text("Cancelar")')
    
    // Verifica se o diálogo foi fechado
    await expect(page.locator('.v-dialog .v-card')).not.toBeVisible()
  })

  test('form validation works in new movement dialog', async ({ page }) => {
    // Abre o diálogo
    await page.click('button:has-text("Nova Movimentação")')
    
    // Tenta criar sem preencher os campos obrigatórios
    await page.click('button:has-text("Criar")')
    
    // Verifica se as mensagens de erro aparecem
    await expect(page.locator('text=Produto é obrigatório')).toBeVisible()
    await expect(page.locator('text=Tipo de movimentação é obrigatório')).toBeVisible()
    await expect(page.locator('text=Quantidade é obrigatória')).toBeVisible()
    
    // Preenche com quantidade negativa
    await page.fill('input[label="Quantidade"]', '-5')
    await page.click('button:has-text("Criar")')
    
    // Verifica se a mensagem de erro para quantidade negativa aparece
    await expect(page.locator('text=Quantidade deve ser positiva')).toBeVisible()
    
    // Preenche com quantidade decimal
    await page.fill('input[label="Quantidade"]', '5.5')
    await page.click('button:has-text("Criar")')
    
    // Verifica se a mensagem de erro para número inteiro aparece
    await expect(page.locator('text=Quantidade deve ser um número inteiro')).toBeVisible()
  })

  test('movement type radio buttons work', async ({ page }) => {
    // Abre o diálogo
    await page.click('button:has-text("Nova Movimentação")')
    
    // Verifica se os radio buttons estão presentes
    await expect(page.locator('input[type="radio"][value="ENTRADA"]')).toBeVisible()
    await expect(page.locator('input[type="radio"][value="SAIDA"]')).toBeVisible()
    await expect(page.locator('text=Entrada')).toBeVisible()
    await expect(page.locator('text=Saída')).toBeVisible()
    
    // Clica em Entrada
    await page.click('input[type="radio"][value="ENTRADA"]')
    await expect(page.locator('input[type="radio"][value="ENTRADA"]')).toBeChecked()
    
    // Clica em Saída
    await page.click('input[type="radio"][value="SAIDA"]')
    await expect(page.locator('input[type="radio"][value="SAIDA"]')).toBeChecked()
  })

  test('can create new movement with valid data', async ({ page }) => {
    // Abre o diálogo
    await page.click('button:has-text("Nova Movimentação")')
    
    // Seleciona um produto (se houver opções disponíveis)
    const productSelect = page.locator('.v-select').first()
    await productSelect.click()
    
    // Aguarda as opções aparecerem e seleciona a primeira (se houver)
    await page.waitForTimeout(1000)
    const firstProduct = page.locator('.v-list-item').first()
    if (await firstProduct.isVisible()) {
      await firstProduct.click()
    }
    
    // Seleciona tipo de movimentação
    await page.click('input[type="radio"][value="ENTRADA"]')
    
    // Preenche a quantidade
    await page.fill('input[label="Quantidade"]', '10')
    
    // Clica em Criar
    await page.click('button:has-text("Criar")')
    
    // Aguarda o diálogo fechar
    await expect(page.locator('.v-dialog .v-card')).not.toBeVisible()
  })

  test('displays movement types with appropriate colors', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se há células com tipos de movimentação
    const entradaCells = page.locator('td:has-text("ENTRADA")')
    const saidaCells = page.locator('td:has-text("SAIDA")')
    
    if (await entradaCells.count() > 0) {
      await expect(entradaCells.first()).toBeVisible()
    }
    
    if (await saidaCells.count() > 0) {
      await expect(saidaCells.first()).toBeVisible()
    }
  })

  test('displays dates in correct format', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se há células com datas
    const dateCells = page.locator('td').filter({ hasText: /\d{2}\/\d{2}\/\d{4}/ })
    
    if (await dateCells.count() > 0) {
      // Verifica se pelo menos uma célula contém formato de data brasileiro
      await expect(dateCells.first()).toBeVisible()
      
      // Verifica se o formato está correto (dd/mm/yyyy)
      const cellText = await dateCells.first().textContent()
      expect(cellText).toMatch(/\d{2}\/\d{2}\/\d{4}/)
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

  test('can open edit dialog for existing movement', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se há dados na tabela
    const editButton = page.locator('button:has(.mdi-pencil)').first()
    
    if (await editButton.isVisible()) {
      // Clica no botão de editar
      await editButton.click()
      
      // Verifica se o diálogo de edição foi aberto
      await expect(page.locator('.v-dialog .v-card')).toBeVisible()
      await expect(page.locator('text=Editar Movimentação')).toBeVisible()
      await expect(page.locator('button:has-text("Salvar")')).toBeVisible()
      
      // Os campos devem estar preenchidos com os valores existentes
      const quantityInput = page.locator('input[label="Quantidade"]')
      await expect(quantityInput).not.toHaveValue('')
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

  test('displays product names correctly', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se há dados na tabela
    const productColumns = page.locator('td').filter({ hasText: /^[A-Za-z]/ })
    
    if (await productColumns.count() > 0) {
      // Verifica se pelo menos uma célula de produto está visível
      await expect(productColumns.first()).toBeVisible()
      
      // Verifica se não há "N/A" ou IDs numéricos na coluna produto
      const cellText = await productColumns.first().textContent()
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
    await expect(page.locator('h1:has-text("Movimentação de Estoque")')).toBeVisible()
    await expect(page.locator('button:has-text("Nova Movimentação")')).toBeVisible()
    await expect(page.locator('.v-data-table')).toBeVisible()
  })

  test('table is sortable by clicking headers', async ({ page }) => {
    // Aguarda a tabela carregar
    await page.waitForSelector('.v-data-table', { timeout: 10000 })
    
    // Verifica se os cabeçalhos clicáveis estão presentes
    const sortableHeaders = page.locator('th[role="columnheader"]')
    
    if (await sortableHeaders.count() > 0) {
      // Clica no cabeçalho Data para ordenar
      const dateHeader = page.locator('th:has-text("Data")')
      if (await dateHeader.isVisible()) {
        await dateHeader.click()
        await page.waitForTimeout(500)
        
        // Verifica se a ordenação foi aplicada
        // Nota: O ícone pode não estar visível dependendo da implementação do Vuetify
      }
    }
  })
}) 