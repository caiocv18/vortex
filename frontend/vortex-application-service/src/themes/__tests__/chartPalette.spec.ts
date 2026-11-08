import { describe, it, expect } from 'vitest'
import {
  CHART_MAX_SERIES,
  CHART_OTHER_COLOR,
  CHART_SERIES_DARK,
  CHART_SERIES_LIGHT,
  chartThemeColors,
  foldTail,
  withAlpha,
} from '../chartPalette'
import { colorThemes } from '../colorThemes'

// Contraste WCAG entre duas cores #rrggbb
function luminance(hex: string): number {
  const value = Number.parseInt(hex.slice(1), 16)
  const [r = 0, g = 0, b = 0] = [(value >> 16) & 255, (value >> 8) & 255, value & 255].map((channel) => {
    const c = channel / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function contrast(a: string, b: string): number {
  const [la, lb] = [luminance(a), luminance(b)]
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05)
}

const lightSurfaces = [...new Set(Object.values(colorThemes).map((t) => t.light.surface))]
const darkSurfaces = [...new Set(Object.values(colorThemes).map((t) => t.dark.surface))]

describe('chart palette', () => {
  it('has one distinct color per series in each theme', () => {
    expect(CHART_SERIES_LIGHT).toHaveLength(CHART_MAX_SERIES)
    expect(CHART_SERIES_DARK).toHaveLength(CHART_MAX_SERIES)
    expect(new Set(CHART_SERIES_LIGHT).size).toBe(CHART_MAX_SERIES)
    expect(new Set(CHART_SERIES_DARK).size).toBe(CHART_MAX_SERIES)
    expect(CHART_SERIES_LIGHT).not.toContain(CHART_OTHER_COLOR)
    expect(CHART_SERIES_DARK).not.toContain(CHART_OTHER_COLOR)
  })

  // Regressão: a paleta antiga tinha #f5ffff (1,0:1 no card claro) e #00262c (1,2:1 no escuro)
  it('keeps every slice visible on the card surface of every color theme', () => {
    for (const surface of lightSurfaces) {
      for (const color of [...CHART_SERIES_LIGHT, CHART_OTHER_COLOR]) {
        expect(contrast(color, surface), `${color} sobre ${surface}`).toBeGreaterThanOrEqual(2)
      }
    }
    for (const surface of darkSurfaces) {
      for (const color of [...CHART_SERIES_DARK, CHART_OTHER_COLOR]) {
        expect(contrast(color, surface), `${color} sobre ${surface}`).toBeGreaterThanOrEqual(2)
      }
    }
  })
})

describe('chartThemeColors', () => {
  it('takes text and grid colors from the dark Vuetify theme', () => {
    const colors = chartThemeColors({
      dark: true,
      colors: { surface: '#2D2D2D', 'on-surface': '#FFF' },
      variables: { 'medium-emphasis-opacity': 0.7, 'border-opacity': 0.12 },
    })
    expect(colors.series).toEqual(CHART_SERIES_DARK)
    expect(colors.surface).toBe('#2D2D2D')
    expect(colors.text).toBe('rgba(255, 255, 255, 0.7)')
    expect(colors.grid).toBe('rgba(255, 255, 255, 0.12)')
    expect(colors.other).toBe(CHART_OTHER_COLOR)
  })

  it('takes text and grid colors from the light Vuetify theme', () => {
    const colors = chartThemeColors({
      dark: false,
      colors: { surface: '#FFFFFF', 'on-surface': '#000000' },
      variables: { 'medium-emphasis-opacity': 0.6, 'border-opacity': 0.12 },
    })
    expect(colors.series).toEqual(CHART_SERIES_LIGHT)
    expect(colors.text).toBe('rgba(0, 0, 0, 0.6)')
    expect(colors.grid).toBe('rgba(0, 0, 0, 0.12)')
  })

  it('falls back to readable defaults when the theme lacks on-surface', () => {
    const colors = chartThemeColors({ dark: true, colors: {}, variables: {} })
    expect(colors.text).toBe('rgba(255, 255, 255, 0.6)')
    expect(colors.grid).toBe('rgba(255, 255, 255, 0.12)')
  })
})

describe('withAlpha', () => {
  it('expands short hex and ignores an existing alpha channel', () => {
    expect(withAlpha('#FFF', 0.5)).toBe('rgba(255, 255, 255, 0.5)')
    expect(withAlpha('#2a78d6', 1)).toBe('rgba(42, 120, 214, 1)')
    expect(withAlpha('#2a78d680', 0.3)).toBe('rgba(42, 120, 214, 0.3)')
  })

  it('returns the input untouched when it is not a hex color', () => {
    expect(withAlpha('rgb(1, 2, 3)', 0.5)).toBe('rgb(1, 2, 3)')
  })
})

describe('foldTail', () => {
  const slices = (n: number) => Array.from({ length: n }, (_, i) => ({ label: `P${i + 1}`, value: n - i }))

  it('keeps up to the palette size as is', () => {
    expect(foldTail(slices(CHART_MAX_SERIES))).toEqual(slices(CHART_MAX_SERIES))
    expect(foldTail([])).toEqual([])
  })

  it('folds the tail into one "Outros" slice with the summed value', () => {
    const folded = foldTail(slices(10))
    expect(folded).toHaveLength(CHART_MAX_SERIES)
    expect(folded.slice(0, CHART_MAX_SERIES - 1)).toEqual(slices(10).slice(0, CHART_MAX_SERIES - 1))
    // P8, P9 e P10 valem 3 + 2 + 1
    expect(folded[folded.length - 1]).toEqual({ label: 'Outros', value: 6, other: true })
  })
})
