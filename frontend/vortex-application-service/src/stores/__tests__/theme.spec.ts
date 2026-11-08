import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useThemeStore } from '../theme'

// Mock do localStorage
const localStorageMock = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  clear: vi.fn()
}
vi.stubGlobal('localStorage', localStorageMock)

// Mock da instância global do Vuetify (window.vuetify); no Vuetify 4 o tema troca por theme.change()
const changeThemeMock = vi.fn<(name: string) => Promise<void>>(() => Promise.resolve())
window.vuetify = { theme: { change: changeThemeMock } } as unknown as Window['vuetify']

describe('Theme Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorageMock.getItem.mockClear()
    localStorageMock.setItem.mockClear()
    changeThemeMock.mockClear()
  })

  it('initializes with light theme by default', () => {
    localStorageMock.getItem.mockReturnValue(null)
    const store = useThemeStore()
    expect(store.isDark).toBe(false)
  })

  it('initializes with dark theme from localStorage', () => {
    localStorageMock.getItem.mockReturnValue('dark')
    const store = useThemeStore()
    expect(store.isDark).toBe(true)
  })

  it('toggles theme correctly', () => {
    localStorageMock.getItem.mockReturnValue('light')
    const store = useThemeStore()

    expect(store.isDark).toBe(false)

    store.toggleTheme()
    expect(store.isDark).toBe(true)
    expect(localStorageMock.setItem).toHaveBeenCalledWith('theme', 'dark')
    expect(changeThemeMock).toHaveBeenLastCalledWith('dark')

    store.toggleTheme()
    expect(store.isDark).toBe(false)
    expect(localStorageMock.setItem).toHaveBeenCalledWith('theme', 'light')
    expect(changeThemeMock).toHaveBeenLastCalledWith('light')
  })

  it('initializes theme correctly', () => {
    localStorageMock.getItem.mockReturnValue('dark')
    const store = useThemeStore()

    store.initTheme()
    expect(store.isDark).toBe(true)
    expect(changeThemeMock).toHaveBeenCalledWith('dark')
  })
})
