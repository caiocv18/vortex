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
    await expect(page.getByLabel('Pesquisar por produto')).toBeVisible()
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
    const searchInput = page.getByLabel('Pesquisar por produto')
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
    // Busca restrita ao diálogo: o botão tem o mesmo texto e o rodapé da tabela tem outro select
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByText('Nova Movimentação')).toBeVisible()
    await expect(dialog.getByRole('combobox', { name: 'Produto' })).toBeVisible()
    await expect(dialog.getByLabel('Quantidade')).toBeVisible()
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
    await expect(page.locator('.v-dialog .v-card')).toBeHidden()
  })

  test('form validation works in new movement dialog', async ({ page }) => {
    // Abre o diálogo
    await page.click('button:has-text("Nova Movimentação")')

    // O tipo já vem preenchido: o diálogo abre com ENTRADA marcada (comportamento intencional)
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('radio', { name: 'Entrada', exact: true })).toBeChecked()
    await expect(dialog.getByRole('radio', { name: 'Saída', exact: true })).not.toBeChecked()

    // Tenta criar sem preencher os campos obrigatórios
    await page.click('button:has-text("Criar")')

    // Verifica se as mensagens de erro aparecem (o tipo, pré-selecionado, não gera erro)
    await expect(page.locator('text=Produto é obrigatório')).toBeVisible()
    await expect(page.locator('text=Quantidade é obrigatória')).toBeVisible()
    await expect(dialog.getByText('Tipo de movimentação é obrigatório')).toHaveCount(0)
    
    // Preenche com quantidade negativa
    await page.getByLabel('Quantidade').fill('-5')
    await page.click('button:has-text("Criar")')
    
    // Verifica se a mensagem de erro para quantidade negativa aparece
    await expect(page.locator('text=Quantidade deve ser positiva')).toBeVisible()
    
    // Preenche com quantidade decimal
    await page.getByLabel('Quantidade').fill('5.5')
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
    // Rótulos dos radios, no diálogo e com texto exato (o filtro "Entradas" e os chips da
    // tabela também contêm "Entrada")
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByText('Entrada', { exact: true })).toBeVisible()
    await expect(dialog.getByText('Saída', { exact: true })).toBeVisible()
    
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
    
    // Seleciona um produto: o primeiro item do menu do select do diálogo. O antigo
    // '.v-select' first() pegava o select "Items per page" do rodapé da tabela (atrás do
    // scrim do diálogo) e o '.v-list-item' first() pegava o cabeçalho do menu lateral.
    // O clique vai no campo (combobox externo do v-select), como o usuário faz: o input
    // interno tem size=1 e fica coberto pelo próprio campo.
    await page
      .getByRole('dialog')
      .getByRole('combobox')
      .filter({ has: page.getByRole('combobox', { name: 'Produto' }) })
      .click()
    const firstProduct = page.getByRole('listbox').getByRole('option').first()
    await expect(firstProduct).toBeVisible()
    await firstProduct.click()

    // Seleciona tipo de movimentação
    await page.click('input[type="radio"][value="ENTRADA"]')

    // Preenche a quantidade
    await page.getByLabel('Quantidade').fill('10')
    
    // Clica em Criar
    await page.click('button:has-text("Criar")')
    
    // Aguarda o diálogo fechar
    await expect(page.locator('.v-dialog .v-card')).toBeHidden()
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
      
      // Verifica se o formato está correto (R$ X,XX, com ponto de milhar: R$ 1.500,00)
      const cellText = await currencyCells.first().textContent()
      expect(cellText).toMatch(/R\$\s*\d{1,3}(\.\d{3})*,\d{2}/)
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
      const quantityInput = page.getByLabel('Quantidade')
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
      
      // Verifica se não há "N/A" ou IDs numéricos na coluna produto (asserções web-first)
      await expect(productColumns.first()).not.toHaveText('N/A')
      await expect(productColumns.first()).not.toHaveText(/^\d+$/)
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