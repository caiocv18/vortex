// Cores dos gráficos (Chart.js) que acompanham o tema claro/escuro do Vuetify.
//
// Séries: paleta categórica de 8 cores, a mesma ordem nos dois temas; o tema escuro usa tons
// ajustados para superfície escura. Validada contra as superfícies do app (#FFFFFF no claro,
// #2D2D2D no escuro): faixa de luminosidade, croma mínimo, separação entre cores vizinhas
// sob daltonismo (protan, deutan e tritan) e em visão normal. Nenhuma cor some no card: todas
// passam de 2:1 de contraste com a superfície de qualquer tema de colorThemes.ts; as que ficam
// abaixo de 3:1 têm o nome na legenda e o valor na tabela ao lado do gráfico.
// A paleta antiga tinha um quase branco (#f5ffff), invisível no card claro, e um quase preto
// (#00262c), invisível no card escuro.
//
// Mais de 8 fatias não ganham cor repetida (duas fatias com a mesma cor confundem quem é quem):
// o excedente vira uma fatia "Outros", em cinza neutro, por foldTail().
//
// Texto (legenda, rótulos dos eixos) e grade saem do tema ativo, pela cor on-surface e pelas
// opacidades do próprio Vuetify (medium-emphasis e border), como o resto da interface.

export const CHART_SERIES_LIGHT = [
  '#2a78d6', // azul
  '#eb6834', // laranja
  '#1baf7a', // verde-água
  '#eda100', // amarelo
  '#e87ba4', // magenta
  '#008300', // verde
  '#6250d6', // violeta
  '#e34948', // vermelho
] as const

export const CHART_SERIES_DARK = [
  '#3987e5',
  '#d95926',
  '#199e70',
  '#c98500',
  '#d55181',
  '#008300',
  '#9085e9',
  '#e66767',
] as const

/** Cor da fatia "Outros": cinza neutro, legível nos dois temas, que não se passa por série. */
export const CHART_OTHER_COLOR = '#8c8b85'

/** Quantas séries têm cor própria; a partir daí o resto vira "Outros". */
export const CHART_MAX_SERIES = CHART_SERIES_LIGHT.length

export function chartSeriesColors(dark: boolean): readonly string[] {
  return dark ? CHART_SERIES_DARK : CHART_SERIES_LIGHT
}

/**
 * O que os gráficos leem do tema do Vuetify (theme.current.value). O tipo Color do Vuetify
 * aceita também número e objetos RGB/HSV; o tema calculado traz hex, e só string é usada.
 */
export interface ChartThemeSource {
  dark: boolean
  colors: Readonly<Record<string, unknown>>
  variables: Readonly<Record<string, unknown>>
}

export interface ChartThemeColors {
  /** Cores das séries/fatias, na ordem */
  series: readonly string[]
  /** Cor da fatia "Outros" */
  other: string
  /** Cor do card (borda entre as fatias do gráfico de pizza) */
  surface: string
  /** Legenda e rótulos dos eixos */
  text: string
  /** Linhas de grade e eixos */
  grid: string
}

/** '#rgb' ou '#rrggbb' (com ou sem alfa) para 'rgba(r, g, b, alpha)'. */
export function withAlpha(hex: string, alpha: number): string {
  let digits = hex.trim().replace(/^#/, '')
  if (digits.length === 3 || digits.length === 4) {
    digits = [...digits.slice(0, 3)].map((d) => d + d).join('')
  }
  const value = Number.parseInt(digits.slice(0, 6), 16)
  if (digits.length < 6 || Number.isNaN(value)) return hex
  return `rgba(${(value >> 16) & 255}, ${(value >> 8) & 255}, ${value & 255}, ${alpha})`
}

function opacity(value: unknown, fallback: number): number {
  const parsed = typeof value === 'number' || typeof value === 'string' ? Number(value) : Number.NaN
  return Number.isFinite(parsed) ? parsed : fallback
}

function color(value: unknown, fallback: string): string {
  return typeof value === 'string' && value !== '' ? value : fallback
}

export function chartThemeColors(theme: ChartThemeSource): ChartThemeColors {
  const onSurface = color(theme.colors['on-surface'], theme.dark ? '#FFFFFF' : '#000000')
  const surface = color(theme.colors.surface, theme.dark ? '#212121' : '#FFFFFF')
  return {
    series: chartSeriesColors(theme.dark),
    other: CHART_OTHER_COLOR,
    surface,
    text: withAlpha(onSurface, opacity(theme.variables['medium-emphasis-opacity'], 0.6)),
    grid: withAlpha(onSurface, opacity(theme.variables['border-opacity'], 0.12)),
  }
}

export interface ChartSlice {
  label: string
  value: number
  /** true na fatia que agrupa o excedente ("Outros") */
  other?: boolean
}

/**
 * Limita as fatias às cores disponíveis: até `max` passam como estão; acima disso ficam as
 * `max - 1` primeiras (o chamador ordena) e o resto é somado numa fatia "Outros".
 */
export function foldTail(
  slices: readonly ChartSlice[],
  max: number = CHART_MAX_SERIES,
  otherLabel = 'Outros',
): ChartSlice[] {
  if (slices.length <= max) return [...slices]
  const head = slices.slice(0, max - 1)
  const rest = slices.slice(max - 1).reduce((sum, slice) => sum + slice.value, 0)
  return [...head, { label: otherLabel, value: rest, other: true }]
}
