import { test, expect } from '@playwright/test'

/**
 * Comportamento comum às telas de cadastro com v-data-table. Fica num spec só, em vez de repetido
 * no spec de cada página, para a mesma verificação não ser copiada em três arquivos.
 */
const dataTablePages = [
  { name: 'Product Types', path: '/product-types' },
  { name: 'Products', path: '/products' },
  { name: 'Movements', path: '/movements' },
]

test.describe('Data tables', () => {
  for (const { name, path } of dataTablePages) {
    test(`${name}: loading indicator clears once the data arrives`, async ({ page }) => {
      // A tabela busca os dados ao montar e mostra a linha de progresso (:loading) enquanto isso.
      // Com a API rápida a linha pode nem aparecer, então a asserção é sobre o fim do carregamento.
      await page.goto(path)

      const table = page.locator('.v-data-table')
      await expect(table).toBeVisible({ timeout: 10000 })
      // A linha .v-data-table-progress só existe com loading=true; some quando a busca termina
      await expect(table.locator('.v-data-table-progress')).toHaveCount(0, { timeout: 10000 })
    })
  }
})
