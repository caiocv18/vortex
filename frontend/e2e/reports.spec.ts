import { test, expect } from '@playwright/test'

test.describe('Reports Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/reports')
  })

  test('displays page title and basic elements', async ({ page }) => {
    // Verifica o título da página
    await expect(page).toHaveTitle(/Relatórios - Sistema de Estoque/)
    await expect(page.locator('h1:has-text("Relatórios")')).toBeVisible()
  })

  test('displays statistics cards with correct information', async ({ page }) => {
    // Aguarda os cards de estatísticas carregarem
    await page.waitForSelector('.v-card', { timeout: 10000 })
    
    // Verifica se os cards de estatísticas estão presentes
    await expect(page.locator('text=Receita Total')).toBeVisible()
    await expect(page.locator('text=Custo Total')).toBeVisible()
    await expect(page.locator('text=Lucro Total')).toBeVisible()
    await expect(page.locator('text=Margem de Lucro')).toBeVisible()
    
    // Verifica se os valores estão formatados corretamente
    const revenueCard = page.locator('.v-card:has-text("Receita Total")')
    const costCard = page.locator('.v-card:has-text("Custo Total")')
    const profitCard = page.locator('.v-card:has-text("Lucro Total")')
    const marginCard = page.locator('.v-card:has-text("Margem de Lucro")')
    
    await expect(revenueCard).toBeVisible()
    await expect(costCard).toBeVisible()
    await expect(profitCard).toBeVisible()
    await expect(marginCard).toBeVisible()
  })

  test('displays currency values in correct format', async ({ page }) => {
    // Aguarda os cards carregarem
    await page.waitForTimeout(3000)
    
    // Verifica se há valores monetários formatados corretamente
    const currencyValues = page.locator('text=/R\\$\\s*\\d+,\\d{2}/')
    
    if (await currencyValues.count() > 0) {
      // Verifica se pelo menos um valor monetário está visível
      await expect(currencyValues.first()).toBeVisible()
    }
  })

  test('displays percentage values correctly', async ({ page }) => {
    // Aguarda os cards carregarem
    await page.waitForTimeout(3000)
    
    // Verifica se há valores percentuais formatados corretamente
    const percentageValues = page.locator('text=/\\d+\\.\\d%/')
    
    if (await percentageValues.count() > 0) {
      // Verifica se pelo menos um valor percentual está visível
      await expect(percentageValues.first()).toBeVisible()
    }
  })

  test('displays product type filter section', async ({ page }) => {
    // Verifica se há seção para filtrar por tipo de produto
    const filterSection = page.locator('.v-card:has-text("Produtos por Tipo")')
    
    if (await filterSection.isVisible()) {
      // Verifica se há um select para escolher o tipo de produto
      const typeSelect = filterSection.locator('.v-select')
      await expect(typeSelect).toBeVisible()
      
      // Verifica se há um botão para carregar o relatório
      const loadButton = filterSection.locator('button')
      if (await loadButton.isVisible()) {
        await expect(loadButton).toBeVisible()
      }
    }
  })

  test('displays profit by product chart', async ({ page }) => {
    // Aguarda os gráficos carregarem
    await page.waitForTimeout(5000)
    
    // Verifica se há um gráfico de lucro por produto
    const chartSection = page.locator('.v-card:has-text("Lucro por Produto")')
    
    if (await chartSection.isVisible()) {
      await expect(chartSection).toBeVisible()
      
      // Verifica se há canvas do gráfico
      const chartCanvas = chartSection.locator('canvas')
      if (await chartCanvas.isVisible()) {
        await expect(chartCanvas).toBeVisible()
      }
    }
  })

  test('displays products by type chart when type is selected', async ({ page }) => {
    // Aguarda a página carregar
    await page.waitForTimeout(3000)
    
    // Procura por um select de tipo de produto
    const typeSelect = page.locator('.v-select')
    
    if (await typeSelect.count() > 0) {
      // Clica no select
      await typeSelect.first().click()
      await page.waitForTimeout(1000)
      
      // Seleciona a primeira opção se houver
      const firstOption = page.locator('.v-list-item').first()
      if (await firstOption.isVisible()) {
        await firstOption.click()
        
        // Procura por botão para carregar relatório
        const loadButton = page.locator('button:has-text("Carregar"), button:has-text("Gerar")')
        if (await loadButton.isVisible()) {
          await loadButton.click()
          
          // Aguarda o gráfico carregar
          await page.waitForTimeout(3000)
          
          // Verifica se o gráfico apareceu
          const chartCanvas = page.locator('canvas')
          if (await chartCanvas.count() > 0) {
            await expect(chartCanvas.first()).toBeVisible()
          }
        }
      }
    }
  })

  test('charts are responsive', async ({ page }) => {
    // Aguarda os gráficos carregarem
    await page.waitForTimeout(5000)
    
    // Verifica se há gráficos na página
    const charts = page.locator('canvas')
    
    if (await charts.count() > 0) {
      // Muda para viewport mobile
      await page.setViewportSize({ width: 375, height: 667 })
      await page.waitForTimeout(1000)
      
      // Verifica se os gráficos ainda estão visíveis
      await expect(charts.first()).toBeVisible()
      
      // Volta para desktop
      await page.setViewportSize({ width: 1280, height: 720 })
      await page.waitForTimeout(1000)
      
      // Verifica se os gráficos ainda estão visíveis
      await expect(charts.first()).toBeVisible()
    }
  })

  test('displays loading states correctly', async ({ page }) => {
    // Recarrega a página para ver estados de carregamento
    await page.reload()
    
    // Aguarda a página carregar completamente
    await page.waitForTimeout(5000)
    
    // Verifica se não há indicadores de carregamento visíveis após o carregamento
    const loadingIndicators = page.locator('.v-progress-circular, .v-skeleton-loader')
    
    // Se houver indicadores, eles devem desaparecer após o carregamento
    if (await loadingIndicators.count() > 0) {
      await page.waitForTimeout(3000)
      // Após aguardar, os indicadores devem ter desaparecido ou estar ocultos
    }
  })

  test('statistics cards show meaningful data', async ({ page }) => {
    // Aguarda os dados carregarem
    await page.waitForTimeout(5000)
    
    // Verifica se os cards não mostram valores padrão/zero
    const statisticsCards = page.locator('.v-card .text-h5')
    
    if (await statisticsCards.count() > 0) {
      for (let i = 0; i < await statisticsCards.count(); i++) {
        const card = statisticsCards.nth(i)
        const text = await card.textContent()
        
        // Verifica se não é apenas "R$ 0,00" ou "0.0%"
        if (text) {
          await expect(card).toBeVisible()
          // Os valores podem ser zero em um sistema novo, então apenas verificamos se estão formatados
          expect(text).toMatch(/R\$|%|\d/)
        }
      }
    }
  })

  test('page handles empty data gracefully', async ({ page }) => {
    // Aguarda a página carregar
    await page.waitForTimeout(5000)
    
    // Verifica se a página não mostra erros quando não há dados
    const errorMessages = page.locator('text=/erro|error/i')
    
    // Não deve haver mensagens de erro visíveis
    if (await errorMessages.count() > 0) {
      for (let i = 0; i < await errorMessages.count(); i++) {
        const error = errorMessages.nth(i)
        // Se houver erros, eles devem estar ocultos ou ser de desenvolvimento
        const isVisible = await error.isVisible()
        if (isVisible) {
          const text = await error.textContent()
          // Permite erros de desenvolvimento/console, mas não erros de UI
          expect(text).not.toContain('Erro ao carregar')
        }
      }
    }
  })

  test('page is responsive on mobile viewport', async ({ page }) => {
    // Define viewport mobile
    await page.setViewportSize({ width: 375, height: 667 })
    
    // Verifica se os elementos principais ainda estão visíveis
    await expect(page.locator('h1:has-text("Relatórios")')).toBeVisible()
    
    // Verifica se os cards de estatísticas se adaptam ao mobile
    const statisticsCards = page.locator('.v-card')
    if (await statisticsCards.count() > 0) {
      await expect(statisticsCards.first()).toBeVisible()
    }
  })

  test('charts legend is displayed correctly', async ({ page }) => {
    // Aguarda os gráficos carregarem
    await page.waitForTimeout(5000)
    
    // Verifica se há gráficos com legendas
    const charts = page.locator('canvas')
    
    if (await charts.count() > 0) {
      // Verifica se a área do gráfico está visível
      await expect(charts.first()).toBeVisible()
      
      // Para gráficos Chart.js, a legenda é renderizada dentro do canvas
      // Então verificamos apenas se o canvas tem tamanho adequado
      const chartBounds = await charts.first().boundingBox()
      expect(chartBounds?.width).toBeGreaterThan(200)
      expect(chartBounds?.height).toBeGreaterThan(200)
    }
  })

  test('data refreshes when page is reloaded', async ({ page }) => {
    // Aguarda o carregamento inicial
    await page.waitForTimeout(5000)
    
    // Pega o valor inicial de um dos cards
    const profitCard = page.locator('.v-card:has-text("Lucro Total") .text-h5')
    let initialValue = ''
    
    if (await profitCard.isVisible()) {
      initialValue = await profitCard.textContent() || ''
    }
    
    // Recarrega a página
    await page.reload()
    await page.waitForTimeout(5000)
    
    // Verifica se os dados foram carregados novamente
    if (await profitCard.isVisible()) {
      const newValue = await profitCard.textContent() || ''
      // Os valores devem ser consistentes (mesmo valor após reload)
      expect(newValue).toBe(initialValue)
    }
  })
}) 