import { test, expect } from '@playwright/test';

// See here how to get started:
// https://playwright.dev/docs/intro
test.describe('Home Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('displays welcome message and system title', async ({ page }) => {
    // Verifica o título da página
    await expect(page).toHaveTitle(/Home - Sistema de Estoque/);
    
    // Aguarda a página carregar
    await page.waitForTimeout(2000)
    
    // Verifica o card de boas-vindas
    await expect(page.locator('text=Bem-vindo ao Sistema de Estoque')).toBeVisible();
    await expect(page.locator('text=Sistema de gerenciamento de estoque Nexdom')).toBeVisible();
  });

  test('displays API connection test section', async ({ page }) => {
    // Verifica se o card de teste de API está presente
    await expect(page.locator('text=Teste de Conexão com API')).toBeVisible();
    await expect(page.locator('button:has-text("Testar Novamente")')).toBeVisible();
    
    // Aguarda o resultado do teste de API aparecer
    await page.waitForTimeout(3000);
    
    // Verifica se há algum resultado (sucesso ou erro)
    const hasSuccess = await page.locator('text=Conexão com API bem-sucedida!').isVisible();
    const hasError = await page.locator('text=Erro ao conectar com a API').isVisible();
    
    expect(hasSuccess || hasError).toBeTruthy();
  });

  test('displays all navigation cards with correct links', async ({ page }) => {
    // Verifica card de Tipos de Produto
    const productTypesCard = page.locator('.v-card:has-text("Tipos de Produto")');
    await expect(productTypesCard).toBeVisible();
    await expect(productTypesCard.locator('text=Gerencie os tipos de produtos do seu estoque.')).toBeVisible();
    await expect(productTypesCard.locator('a[href="/product-types"]')).toBeVisible();

    // Verifica card de Produtos
    const productsCard = page.locator('.v-card:has-text("Produtos")');
    await expect(productsCard).toBeVisible();
    await expect(productsCard.locator('text=Cadastre e gerencie todos os produtos.')).toBeVisible();
    await expect(productsCard.locator('a[href="/products"]')).toBeVisible();

    // Verifica card de Movimentação
    const movementsCard = page.locator('.v-card:has-text("Movimentação")');
    await expect(movementsCard).toBeVisible();
    await expect(movementsCard.locator('text=Registre movimentações de estoque.')).toBeVisible();
    await expect(movementsCard.locator('a[href="/movements"]')).toBeVisible();

    // Verifica card de Relatórios
    const reportsCard = page.locator('.v-card:has-text("Relatórios")');
    await expect(reportsCard).toBeVisible();
    await expect(reportsCard.locator('text=Visualize relatórios detalhados.')).toBeVisible();
    await expect(reportsCard.locator('a[href="/reports"]')).toBeVisible();
  });

  test('navigation cards have correct icons', async ({ page }) => {
    // Verifica ícones dos cards
    await expect(page.locator('.mdi-shape')).toBeVisible(); // Tipos de Produto
    await expect(page.locator('.mdi-package-variant')).toBeVisible(); // Produtos  
    await expect(page.locator('.mdi-swap-horizontal')).toBeVisible(); // Movimentação
    await expect(page.locator('.mdi-chart-box')).toBeVisible(); // Relatórios
  });

  test('can navigate to product types from home card', async ({ page }) => {
    await page.click('text=Acessar >> nth=0');
    await expect(page).toHaveURL(/\/product-types/);
    await expect(page.locator('h1:has-text("Tipos de Produto")')).toBeVisible();
  });

  test('can navigate to products from home card', async ({ page }) => {
    await page.click('text=Acessar >> nth=1');
    await expect(page).toHaveURL(/\/products/);
    await expect(page.locator('h1:has-text("Produtos")')).toBeVisible();
  });

  test('can navigate to movements from home card', async ({ page }) => {
    await page.click('text=Acessar >> nth=2');
    await expect(page).toHaveURL(/\/movements/);
    await expect(page.locator('h1:has-text("Movimentação de Estoque")')).toBeVisible();
  });

  test('can navigate to reports from home card', async ({ page }) => {
    await page.click('text=Acessar >> nth=3');
    await expect(page).toHaveURL(/\/reports/);
    await expect(page.locator('h1:has-text("Relatórios")')).toBeVisible();
  });
});
