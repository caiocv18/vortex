import { test, expect } from "@playwright/test"

test.describe("Accessibility Tests", () => {
  test("all pages have proper heading structure", async ({ page }) => {
    const pages = [
      { path: "/", expectedText: "Bem-vindo ao Sistema de Estoque" },
      { path: "/product-types", expectedH1: "Tipos de Produto" },
      { path: "/products", expectedH1: "Produtos" },
      { path: "/movements", expectedH1: "Movimentação de Estoque" },
      { path: "/reports", expectedH1: "Relatórios" }
    ]

    for (const { path, expectedH1, expectedText } of pages) {
      await page.goto(path)
      await page.waitForTimeout(2000)
      
      if (expectedH1) {
        const h1Elements = page.locator("h1")
        await expect(h1Elements).toHaveCount(1)
        await expect(h1Elements).toContainText(expectedH1)
      } else if (expectedText) {
        // Para a home page, verifica se o texto principal está presente
        await expect(page.locator(`text=${expectedText}`)).toBeVisible()
      }
    }
  })

  test("page loads within reasonable time", async ({ page }) => {
    const pages = ["/", "/product-types", "/products", "/movements", "/reports"]
    for (const path of pages) {
      const startTime = Date.now()
      await page.goto(path)
      await page.waitForLoadState("networkidle")
      const loadTime = Date.now() - startTime
      expect(loadTime).toBeLessThan(10000)
      const mainContent = page.locator("main, .v-main")
      await expect(mainContent).toBeVisible()
    }
  })
})
