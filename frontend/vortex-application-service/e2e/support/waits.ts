import { expect, type Locator, type Page } from '@playwright/test'

// Waits on observable conditions shared by the specs, in place of fixed timeouts.

/** Theme saved by the theme store in localStorage ('light', 'dark' or null before the first toggle). */
export const readStoredTheme = (page: Page) => page.evaluate(() => localStorage.getItem('theme'))

/** Waits until the stored theme differs from `previous` and returns the new value. */
export async function waitForStoredThemeChange(page: Page, previous: string | null) {
  await expect.poll(() => readStoredTheme(page)).not.toBe(previous)
  return readStoredTheme(page)
}

/** Navigation drawer; it carries the --rail modifier while the rail mode is on. */
export const navigationDrawer = (page: Page) => page.locator('.v-navigation-drawer')

/** Clicks the rail toggle and waits until the drawer reaches the requested rail state. */
export async function toggleRail(page: Page, toggle: Locator, railOn: boolean) {
  await toggle.click()
  const railClass = /v-navigation-drawer--rail/
  const drawer = navigationDrawer(page)
  if (railOn) {
    await expect(drawer).toHaveClass(railClass)
  } else {
    await expect(drawer).not.toHaveClass(railClass)
  }
}

/** Result of the API connection test on the home page: success or error, whichever is shown. */
export const apiTestResult = (page: Page) =>
  page
    .getByText('Conexão com API bem-sucedida!')
    .or(page.getByText('Erro ao conectar com a API'))
    .first()

/** Endpoint the reports page fetches on mount, after the product types. */
export const PROFIT_REPORT_PATH = '/api/relatorios/lucro-por-produto'

/**
 * Progress bars of the reports themselves (v-if inside v-card-text). Every v-card also keeps an
 * idle bar in .v-card__loader, which stays in the DOM and says nothing about loading.
 */
export const reportProgressBars = (page: Page) => page.locator('.v-card-text > .v-progress-linear')

/**
 * Runs `action` (a navigation or a reload of the reports page) and waits for the profit report
 * response and for its progress bar to go away, so the cards, table and chart are final.
 * The listener is registered before the action, so a fast response is not missed.
 */
export async function loadReports(page: Page, action: () => Promise<unknown>) {
  const response = page.waitForResponse((r) => r.url().includes(PROFIT_REPORT_PATH))
  await action()
  await response
  await expect(reportProgressBars(page)).toHaveCount(0)
}
