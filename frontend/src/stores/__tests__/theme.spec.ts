import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useThemeStore } from '../theme'

// Mock do localStorage
const localStorageMock = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  clear: vi.fn()
}
global.localStorage = localStorageMock as any

// Mock do useTheme
vi.mock('vuetify', () => ({
  useTheme: () => ({
    global: {
      name: {
        value: 'light'
      }
    }
  })
}))

describe('Theme Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorageMock.getItem.mockClear()
    localStorageMock.setItem.mockClear()
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
    
    store.toggleTheme()
    expect(store.isDark).toBe(false)
    expect(localStorageMock.setItem).toHaveBeenCalledWith('theme', 'light')
  })

  it('initializes theme correctly', () => {
    localStorageMock.getItem.mockReturnValue('dark')
    const store = useThemeStore()
    
    store.initTheme()
    expect(store.isDark).toBe(true)
  })
}) 