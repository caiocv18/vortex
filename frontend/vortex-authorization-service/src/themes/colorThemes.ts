// Definições de temas de cores para o sistema Vortex - Frontend de Autorização
// Sincronizado com frontend/vortex-application-service/src/themes/colorThemes.ts

export type AuthColorTheme = {
  name: string
  displayName: string
  primary: {
    50: string
    100: string
    200: string
    300: string
    400: string
    500: string
    600: string
    700: string
    800: string
    900: string
  }
  secondary: {
    50: string
    100: string
    200: string
    300: string
    400: string
    500: string
    600: string
    700: string
    800: string
    900: string
  }
  cssVars: {
    primaryColor: string
    primaryDark: string
    primaryLight: string
    secondaryColor: string
    secondaryDark: string
    secondaryLight: string
    successColor: string
    errorColor: string
    warningColor: string
    infoColor: string
  }
}

export const authColorThemes: Record<string, AuthColorTheme> = {
  vermelho: {
    name: 'vermelho',
    displayName: 'Vermelho',
    primary: {
      50: '#fef2f2',
      100: '#fee2e2', 
      200: '#fecaca',
      300: '#fca5a5',
      400: '#f87171',
      500: '#ef4444',
      600: '#dc2626',
      700: '#b91c1c',
      800: '#991b1b',
      900: '#7f1d1d'
    },
    secondary: {
      50: '#fdf2f8',
      100: '#fce7f3',
      200: '#fbcfe8',
      300: '#f9a8d4',
      400: '#f472b6',
      500: '#ec4899',
      600: '#db2777',
      700: '#be185d',
      800: '#9d174d',
      900: '#831843'
    },
    cssVars: {
      primaryColor: '#ef4444',
      primaryDark: '#dc2626',
      primaryLight: '#f87171',
      secondaryColor: '#ec4899',
      secondaryDark: '#db2777',
      secondaryLight: '#f472b6',
      successColor: '#10b981',
      errorColor: '#ef4444',
      warningColor: '#f59e0b',
      infoColor: '#3b82f6'
    }
  },

  verde: {
    name: 'verde',
    displayName: 'Verde',
    primary: {
      50: '#f0fdf4',
      100: '#dcfce7',
      200: '#bbf7d0',
      300: '#86efac',
      400: '#4ade80',
      500: '#22c55e',
      600: '#16a34a',
      700: '#15803d',
      800: '#166534',
      900: '#14532d'
    },
    secondary: {
      50: '#f0fdfa',
      100: '#ccfbf1',
      200: '#99f6e4',
      300: '#5eead4',
      400: '#2dd4bf',
      500: '#14b8a6',
      600: '#0d9488',
      700: '#0f766e',
      800: '#115e59',
      900: '#134e4a'
    },
    cssVars: {
      primaryColor: '#22c55e',
      primaryDark: '#16a34a',
      primaryLight: '#4ade80',
      secondaryColor: '#14b8a6',
      secondaryDark: '#0d9488',
      secondaryLight: '#2dd4bf',
      successColor: '#22c55e',
      errorColor: '#ef4444',
      warningColor: '#f59e0b',
      infoColor: '#3b82f6'
    }
  },

  azul: {
    name: 'azul',
    displayName: 'Azul',
    primary: {
      50: '#eff6ff',
      100: '#dbeafe',
      200: '#bfdbfe',
      300: '#93c5fd',
      400: '#60a5fa',
      500: '#3b82f6',
      600: '#2563eb',
      700: '#1d4ed8',
      800: '#1e40af',
      900: '#1e3a8a'
    },
    secondary: {
      50: '#f0f9ff',
      100: '#e0f2fe',
      200: '#bae6fd',
      300: '#7dd3fc',
      400: '#38bdf8',
      500: '#0ea5e9',
      600: '#0284c7',
      700: '#0369a1',
      800: '#075985',
      900: '#0c4a6e'
    },
    cssVars: {
      primaryColor: '#3b82f6',
      primaryDark: '#2563eb',
      primaryLight: '#60a5fa',
      secondaryColor: '#0ea5e9',
      secondaryDark: '#0284c7',
      secondaryLight: '#38bdf8',
      successColor: '#10b981',
      errorColor: '#ef4444',
      warningColor: '#f59e0b',
      infoColor: '#3b82f6'
    }
  },

  laranja: {
    name: 'laranja',
    displayName: 'Laranja',
    primary: {
      50: '#fff7ed',
      100: '#ffedd5',
      200: '#fed7aa',
      300: '#fdba74',
      400: '#fb923c',
      500: '#f97316',
      600: '#ea580c',
      700: '#c2410c',
      800: '#9a3412',
      900: '#7c2d12'
    },
    secondary: {
      50: '#fefce8',
      100: '#fef9c3',
      200: '#fef08a',
      300: '#fde047',
      400: '#facc15',
      500: '#eab308',
      600: '#ca8a04',
      700: '#a16207',
      800: '#854d0e',
      900: '#713f12'
    },
    cssVars: {
      primaryColor: '#f97316',
      primaryDark: '#ea580c',
      primaryLight: '#fb923c',
      secondaryColor: '#eab308',
      secondaryDark: '#ca8a04',
      secondaryLight: '#facc15',
      successColor: '#10b981',
      errorColor: '#ef4444',
      warningColor: '#f97316',
      infoColor: '#3b82f6'
    }
  },

  roxo: {
    name: 'roxo',
    displayName: 'Roxo',
    primary: {
      50: '#faf5ff',
      100: '#f3e8ff',
      200: '#e9d5ff',
      300: '#d8b4fe',
      400: '#c084fc',
      500: '#a855f7',
      600: '#9333ea',
      700: '#7c3aed',
      800: '#6b21a8',
      900: '#581c87'
    },
    secondary: {
      50: '#f8fafc',
      100: '#f1f5f9',
      200: '#e2e8f0',
      300: '#cbd5e1',
      400: '#94a3b8',
      500: '#64748b',
      600: '#475569',
      700: '#334155',
      800: '#1e293b',
      900: '#0f172a'
    },
    cssVars: {
      primaryColor: '#a855f7',
      primaryDark: '#9333ea',
      primaryLight: '#c084fc',
      secondaryColor: '#64748b',
      secondaryDark: '#475569',
      secondaryLight: '#94a3b8',
      successColor: '#10b981',
      errorColor: '#ef4444',
      warningColor: '#f59e0b',
      infoColor: '#a855f7'
    }
  },

  rosa: {
    name: 'rosa',
    displayName: 'Rosa',
    primary: {
      50: '#fdf2f8',
      100: '#fce7f3',
      200: '#fbcfe8',
      300: '#f9a8d4',
      400: '#f472b6',
      500: '#ec4899',
      600: '#db2777',
      700: '#be185d',
      800: '#9d174d',
      900: '#831843'
    },
    secondary: {
      50: '#fff1f2',
      100: '#ffe4e6',
      200: '#fecdd3',
      300: '#fda4af',
      400: '#fb7185',
      500: '#f43f5e',
      600: '#e11d48',
      700: '#be123c',
      800: '#9f1239',
      900: '#881337'
    },
    cssVars: {
      primaryColor: '#ec4899',
      primaryDark: '#db2777',
      primaryLight: '#f472b6',
      secondaryColor: '#f43f5e',
      secondaryDark: '#e11d48',
      secondaryLight: '#fb7185',
      successColor: '#10b981',
      errorColor: '#ef4444',
      warningColor: '#f59e0b',
      infoColor: '#3b82f6'
    }
  },

  azulClaro: {
    name: 'azulClaro',
    displayName: 'Azul Claro',
    primary: {
      50: '#f0f9ff',
      100: '#e0f2fe',
      200: '#bae6fd',
      300: '#7dd3fc',
      400: '#38bdf8',
      500: '#0ea5e9',
      600: '#0284c7',
      700: '#0369a1',
      800: '#075985',
      900: '#0c4a6e'
    },
    secondary: {
      50: '#ecfeff',
      100: '#cffafe',
      200: '#a5f3fc',
      300: '#67e8f9',
      400: '#22d3ee',
      500: '#06b6d4',
      600: '#0891b2',
      700: '#0e7490',
      800: '#155e75',
      900: '#164e63'
    },
    cssVars: {
      primaryColor: '#0ea5e9',
      primaryDark: '#0284c7',
      primaryLight: '#38bdf8',
      secondaryColor: '#06b6d4',
      secondaryDark: '#0891b2',
      secondaryLight: '#22d3ee',
      successColor: '#10b981',
      errorColor: '#ef4444',
      warningColor: '#f59e0b',
      infoColor: '#0ea5e9'
    }
  },

  azulEscuro: {
    name: 'azulEscuro',
    displayName: 'Azul Escuro',
    primary: {
      50: '#f8fafc',
      100: '#f1f5f9',
      200: '#e2e8f0',
      300: '#cbd5e1',
      400: '#94a3b8',
      500: '#64748b',
      600: '#475569',
      700: '#334155',
      800: '#1e293b',
      900: '#0f172a'
    },
    secondary: {
      50: '#f0f9ff',
      100: '#e0f2fe',
      200: '#bae6fd',
      300: '#7dd3fc',
      400: '#38bdf8',
      500: '#0ea5e9',
      600: '#0284c7',
      700: '#0369a1',
      800: '#075985',
      900: '#0c4a6e'
    },
    cssVars: {
      primaryColor: '#334155',
      primaryDark: '#1e293b',
      primaryLight: '#64748b',
      secondaryColor: '#0ea5e9',
      secondaryDark: '#0284c7',
      secondaryLight: '#38bdf8',
      successColor: '#10b981',
      errorColor: '#ef4444',
      warningColor: '#f59e0b',
      infoColor: '#0ea5e9'
    }
  },

  amarelo: {
    name: 'amarelo',
    displayName: 'Amarelo',
    primary: {
      50: '#fefce8',
      100: '#fef9c3',
      200: '#fef08a',
      300: '#fde047',
      400: '#facc15',
      500: '#eab308',
      600: '#ca8a04',
      700: '#a16207',
      800: '#854d0e',
      900: '#713f12'
    },
    secondary: {
      50: '#fff7ed',
      100: '#ffedd5',
      200: '#fed7aa',
      300: '#fdba74',
      400: '#fb923c',
      500: '#f97316',
      600: '#ea580c',
      700: '#c2410c',
      800: '#9a3412',
      900: '#7c2d12'
    },
    cssVars: {
      primaryColor: '#eab308',
      primaryDark: '#ca8a04',
      primaryLight: '#facc15',
      secondaryColor: '#f97316',
      secondaryDark: '#ea580c',
      secondaryLight: '#fb923c',
      successColor: '#10b981',
      errorColor: '#ef4444',
      warningColor: '#eab308',
      infoColor: '#3b82f6'
    }
  },

  cinza: {
    name: 'cinza',
    displayName: 'Cinza',
    primary: {
      50: '#f9fafb',
      100: '#f3f4f6',
      200: '#e5e7eb',
      300: '#d1d5db',
      400: '#9ca3af',
      500: '#6b7280',
      600: '#4b5563',
      700: '#374151',
      800: '#1f2937',
      900: '#111827'
    },
    secondary: {
      50: '#f8fafc',
      100: '#f1f5f9',
      200: '#e2e8f0',
      300: '#cbd5e1',
      400: '#94a3b8',
      500: '#64748b',
      600: '#475569',
      700: '#334155',
      800: '#1e293b',
      900: '#0f172a'
    },
    cssVars: {
      primaryColor: '#6b7280',
      primaryDark: '#4b5563',
      primaryLight: '#9ca3af',
      secondaryColor: '#64748b',
      secondaryDark: '#475569',
      secondaryLight: '#94a3b8',
      successColor: '#10b981',
      errorColor: '#ef4444',
      warningColor: '#f59e0b',
      infoColor: '#3b82f6'
    }
  },

  preto: {
    name: 'preto',
    displayName: 'Preto',
    primary: {
      50: '#f9fafb',
      100: '#f3f4f6',
      200: '#e5e7eb',
      300: '#d1d5db',
      400: '#9ca3af',
      500: '#6b7280',
      600: '#4b5563',
      700: '#374151',
      800: '#1f2937',
      900: '#111827'
    },
    secondary: {
      50: '#f8fafc',
      100: '#f1f5f9',
      200: '#e2e8f0',
      300: '#cbd5e1',
      400: '#94a3b8',
      500: '#64748b',
      600: '#475569',
      700: '#334155',
      800: '#1e293b',
      900: '#0f172a'
    },
    cssVars: {
      primaryColor: '#111827',
      primaryDark: '#000000',
      primaryLight: '#374151',
      secondaryColor: '#4b5563',
      secondaryDark: '#374151',
      secondaryLight: '#6b7280',
      successColor: '#10b981',
      errorColor: '#ef4444',
      warningColor: '#f59e0b',
      infoColor: '#3b82f6'
    }
  },

  branco: {
    name: 'branco',
    displayName: 'Branco',
    primary: {
      50: '#ffffff',
      100: '#f9fafb',
      200: '#f3f4f6',
      300: '#e5e7eb',
      400: '#d1d5db',
      500: '#9ca3af',
      600: '#6b7280',
      700: '#4b5563',
      800: '#374151',
      900: '#1f2937'
    },
    secondary: {
      50: '#f0f9ff',
      100: '#e0f2fe',
      200: '#bae6fd',
      300: '#7dd3fc',
      400: '#38bdf8',
      500: '#0ea5e9',
      600: '#0284c7',
      700: '#0369a1',
      800: '#075985',
      900: '#0c4a6e'
    },
    cssVars: {
      primaryColor: '#f9fafb',
      primaryDark: '#e5e7eb',
      primaryLight: '#ffffff',
      secondaryColor: '#0ea5e9',
      secondaryDark: '#0284c7',
      secondaryLight: '#38bdf8',
      successColor: '#10b981',
      errorColor: '#ef4444',
      warningColor: '#f59e0b',
      infoColor: '#0ea5e9'
    }
  }
}

// Função para obter tema por nome
export function getAuthThemeByName(themeName: string): AuthColorTheme | null {
  return authColorThemes[themeName] || null
}

// Lista de temas disponíveis
export const availableAuthThemes = Object.keys(authColorThemes)

// Tema padrão (verde - original do Vortex)
export const defaultAuthTheme = 'verde'