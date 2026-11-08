import { useEffect, useMemo } from 'react'
import { getAuthThemeByName, defaultAuthTheme } from '@/themes/colorThemes'

// Hook para gerenciar tema de cores no frontend de autorização
export function useTheme() {
  // Obter tema selecionado da variável de ambiente
  const selectedTheme = import.meta.env.VITE_THEME_COLOR || defaultAuthTheme
  
  // Obter configuração do tema
  const themeConfig = useMemo(() => {
    const theme = getAuthThemeByName(selectedTheme)
    const fallbackTheme = getAuthThemeByName(defaultAuthTheme)!
    
    return theme || fallbackTheme
  }, [selectedTheme])

  // Aplicar variáveis CSS no :root
  useEffect(() => {
    const root = document.documentElement
    const { cssVars, primary, secondary } = themeConfig
    
    // Aplicar variáveis CSS customizadas (para CSS vanilla)
    root.style.setProperty('--primary-color', cssVars.primaryColor)
    root.style.setProperty('--primary-dark', cssVars.primaryDark)
    root.style.setProperty('--primary-light', cssVars.primaryLight)
    root.style.setProperty('--secondary-color', cssVars.secondaryColor)
    root.style.setProperty('--secondary-dark', cssVars.secondaryDark)
    root.style.setProperty('--secondary-light', cssVars.secondaryLight)
    root.style.setProperty('--success-color', cssVars.successColor)
    root.style.setProperty('--error-color', cssVars.errorColor)
    root.style.setProperty('--warning-color', cssVars.warningColor)
    root.style.setProperty('--info-color', cssVars.infoColor)
    
    // Converter hex para RGB para Tailwind
    const hexToRgb = (hex: string) => {
      const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
      return result ? 
        `${parseInt(result[1], 16)} ${parseInt(result[2], 16)} ${parseInt(result[3], 16)}` : 
        '0 0 0'
    }
    
    // Aplicar variáveis CSS para Tailwind (lidas pelo @theme inline de src/index.css).
    // Prefixo --vx-: o --tw- é reservado às variáveis internas do Tailwind 4.
    Object.entries(primary).forEach(([key, value]) => {
      root.style.setProperty(`--vx-primary-${key}`, hexToRgb(value))
    })

    Object.entries(secondary).forEach(([key, value]) => {
      root.style.setProperty(`--vx-secondary-${key}`, hexToRgb(value))
    })
    
    console.log(`🎨 [Auth Theme] Applied ${themeConfig.displayName} theme (${selectedTheme})`)
    
    // Cleanup não é necessário pois queremos manter as variáveis
  }, [themeConfig, selectedTheme])

  return {
    currentTheme: themeConfig,
    themeName: selectedTheme,
    colors: themeConfig.primary,
    secondaryColors: themeConfig.secondary,
    cssVars: themeConfig.cssVars
  }
}