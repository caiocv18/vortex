// Definições de temas de cores para o sistema Vortex
// Cada cor tem versões clara e escura otimizadas para acessibilidade

export type ColorTheme = {
  name: string
  displayName: string
  light: {
    primary: string
    secondary: string
    background: string
    surface: string
    error: string
    info: string
    success: string
    warning: string
  }
  dark: {
    primary: string
    secondary: string
    background: string
    surface: string
    error: string
    info: string
    success: string
    warning: string
  }
}

export const colorThemes: Record<string, ColorTheme> = {
  vermelho: {
    name: 'vermelho',
    displayName: 'Vermelho',
    light: {
      primary: '#E53E3E',
      secondary: '#742A2A',
      background: '#FFF5F5',
      surface: '#FFFFFF',
      error: '#E53E3E',
      info: '#3182CE',
      success: '#38A169',
      warning: '#DD6B20'
    },
    dark: {
      primary: '#FC8181',
      secondary: '#E53E3E',
      background: '#1A1A1A',
      surface: '#2D2D2D',
      error: '#FC8181',
      info: '#63B3ED',
      success: '#68D391',
      warning: '#F6AD55'
    }
  },

  verde: {
    name: 'verde',
    displayName: 'Verde',
    light: {
      primary: '#38A169',
      secondary: '#2F855A',
      background: '#F0FFF4',
      surface: '#FFFFFF',
      error: '#E53E3E',
      info: '#3182CE',
      success: '#38A169',
      warning: '#DD6B20'
    },
    dark: {
      primary: '#68D391',
      secondary: '#38A169',
      background: '#1A1A1A',
      surface: '#2D2D2D',
      error: '#FC8181',
      info: '#63B3ED',
      success: '#68D391',
      warning: '#F6AD55'
    }
  },

  azul: {
    name: 'azul',
    displayName: 'Azul',
    light: {
      primary: '#3182CE',
      secondary: '#2C5282',
      background: '#EBF8FF',
      surface: '#FFFFFF',
      error: '#E53E3E',
      info: '#3182CE',
      success: '#38A169',
      warning: '#DD6B20'
    },
    dark: {
      primary: '#63B3ED',
      secondary: '#3182CE',
      background: '#1A1A1A',
      surface: '#2D2D2D',
      error: '#FC8181',
      info: '#63B3ED',
      success: '#68D391',
      warning: '#F6AD55'
    }
  },

  laranja: {
    name: 'laranja',
    displayName: 'Laranja',
    light: {
      primary: '#DD6B20',
      secondary: '#C05621',
      background: '#FFFAF0',
      surface: '#FFFFFF',
      error: '#E53E3E',
      info: '#3182CE',
      success: '#38A169',
      warning: '#DD6B20'
    },
    dark: {
      primary: '#F6AD55',
      secondary: '#DD6B20',
      background: '#1A1A1A',
      surface: '#2D2D2D',
      error: '#FC8181',
      info: '#63B3ED',
      success: '#68D391',
      warning: '#F6AD55'
    }
  },

  roxo: {
    name: 'roxo',
    displayName: 'Roxo',
    light: {
      primary: '#805AD5',
      secondary: '#553C9A',
      background: '#FAF5FF',
      surface: '#FFFFFF',
      error: '#E53E3E',
      info: '#3182CE',
      success: '#38A169',
      warning: '#DD6B20'
    },
    dark: {
      primary: '#B794F6',
      secondary: '#805AD5',
      background: '#1A1A1A',
      surface: '#2D2D2D',
      error: '#FC8181',
      info: '#63B3ED',
      success: '#68D391',
      warning: '#F6AD55'
    }
  },

  rosa: {
    name: 'rosa',
    displayName: 'Rosa',
    light: {
      primary: '#D53F8C',
      secondary: '#B83280',
      background: '#FFF5F7',
      surface: '#FFFFFF',
      error: '#E53E3E',
      info: '#3182CE',
      success: '#38A169',
      warning: '#DD6B20'
    },
    dark: {
      primary: '#F687B3',
      secondary: '#D53F8C',
      background: '#1A1A1A',
      surface: '#2D2D2D',
      error: '#FC8181',
      info: '#63B3ED',
      success: '#68D391',
      warning: '#F6AD55'
    }
  },

  azulClaro: {
    name: 'azulClaro',
    displayName: 'Azul Claro',
    light: {
      primary: '#63B3ED',
      secondary: '#4299E1',
      background: '#EBF8FF',
      surface: '#FFFFFF',
      error: '#E53E3E',
      info: '#63B3ED',
      success: '#38A169',
      warning: '#DD6B20'
    },
    dark: {
      primary: '#90CDF4',
      secondary: '#63B3ED',
      background: '#1A1A1A',
      surface: '#2D2D2D',
      error: '#FC8181',
      info: '#90CDF4',
      success: '#68D391',
      warning: '#F6AD55'
    }
  },

  azulEscuro: {
    name: 'azulEscuro',
    displayName: 'Azul Escuro',
    light: {
      primary: '#2C5282',
      secondary: '#1A365D',
      background: '#EBF4FF',
      surface: '#FFFFFF',
      error: '#E53E3E',
      info: '#3182CE',
      success: '#38A169',
      warning: '#DD6B20'
    },
    dark: {
      primary: '#4299E1',
      secondary: '#2C5282',
      background: '#1A1A1A',
      surface: '#2D2D2D',
      error: '#FC8181',
      info: '#63B3ED',
      success: '#68D391',
      warning: '#F6AD55'
    }
  },

  amarelo: {
    name: 'amarelo',
    displayName: 'Amarelo',
    light: {
      primary: '#D69E2E',
      secondary: '#B7791F',
      background: '#FFFFF0',
      surface: '#FFFFFF',
      error: '#E53E3E',
      info: '#3182CE',
      success: '#38A169',
      warning: '#D69E2E'
    },
    dark: {
      primary: '#F6E05E',
      secondary: '#D69E2E',
      background: '#1A1A1A',
      surface: '#2D2D2D',
      error: '#FC8181',
      info: '#63B3ED',
      success: '#68D391',
      warning: '#F6E05E'
    }
  },

  cinza: {
    name: 'cinza',
    displayName: 'Cinza',
    light: {
      primary: '#4A5568',
      secondary: '#2D3748',
      background: '#F7FAFC',
      surface: '#FFFFFF',
      error: '#E53E3E',
      info: '#3182CE',
      success: '#38A169',
      warning: '#DD6B20'
    },
    dark: {
      primary: '#A0AEC0',
      secondary: '#718096',
      background: '#1A1A1A',
      surface: '#2D2D2D',
      error: '#FC8181',
      info: '#63B3ED',
      success: '#68D391',
      warning: '#F6AD55'
    }
  },

  preto: {
    name: 'preto',
    displayName: 'Preto',
    light: {
      primary: '#1A202C',
      secondary: '#2D3748',
      background: '#F7FAFC',
      surface: '#FFFFFF',
      error: '#E53E3E',
      info: '#3182CE',
      success: '#38A169',
      warning: '#DD6B20'
    },
    dark: {
      primary: '#E2E8F0',
      secondary: '#CBD5E0',
      background: '#0D1117',
      surface: '#161B22',
      error: '#FC8181',
      info: '#63B3ED',
      success: '#68D391',
      warning: '#F6AD55'
    }
  },

  branco: {
    name: 'branco',
    displayName: 'Branco',
    light: {
      primary: '#F7FAFC',
      secondary: '#E2E8F0',
      background: '#FFFFFF',
      surface: '#F7FAFC',
      error: '#E53E3E',
      info: '#3182CE',
      success: '#38A169',
      warning: '#DD6B20'
    },
    dark: {
      primary: '#2D3748',
      secondary: '#4A5568',
      background: '#1A1A1A',
      surface: '#2D2D2D',
      error: '#FC8181',
      info: '#63B3ED',
      success: '#68D391',
      warning: '#F6AD55'
    }
  }
}

// Função para obter tema por nome
export function getThemeByName(themeName: string): ColorTheme | null {
  return colorThemes[themeName] || null
}

// Lista de temas disponíveis
export const availableThemes = Object.keys(colorThemes)

// Tema padrão (verde - original do Vortex)
export const defaultTheme = 'verde'