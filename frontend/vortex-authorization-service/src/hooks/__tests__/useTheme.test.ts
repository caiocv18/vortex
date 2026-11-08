import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useTheme } from '@/hooks/useTheme';

const SHADES = ['50', '100', '200', '300', '400', '500', '600', '700', '800', '900'];
const RGB_TRIPLET = /^\d{1,3} \d{1,3} \d{1,3}$/;

function rootVar(name: string): string {
  return document.documentElement.style.getPropertyValue(name);
}

describe('useTheme', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    document.documentElement.removeAttribute('style');
  });

  it('aplica no :root as variáveis do tema escolhido em VITE_THEME_COLOR', () => {
    vi.stubEnv('VITE_THEME_COLOR', 'azul');

    const { result } = renderHook(() => useTheme());

    expect(result.current.themeName).toBe('azul');
    expect(result.current.currentTheme.name).toBe('azul');

    // Variáveis de CSS puro (index.css, Toaster)
    expect(rootVar('--primary-color')).toBe('#3b82f6');
    expect(rootVar('--secondary-color')).toBe('#0ea5e9');
    expect(rootVar('--success-color')).toBe('#10b981');

    // Trios "r g b" lidos pelo @theme inline do Tailwind 4 (azul: primary-500 #3b82f6, secondary-500 #0ea5e9)
    expect(rootVar('--vx-primary-500')).toBe('59 130 246');
    expect(rootVar('--vx-primary-50')).toBe('239 246 255');
    expect(rootVar('--vx-secondary-500')).toBe('14 165 233');
    for (const shade of SHADES) {
      expect(rootVar(`--vx-primary-${shade}`)).toMatch(RGB_TRIPLET);
      expect(rootVar(`--vx-secondary-${shade}`)).toMatch(RGB_TRIPLET);
    }
  });

  it('não grava mais o prefixo --tw-, reservado ao Tailwind 4', () => {
    vi.stubEnv('VITE_THEME_COLOR', 'azul');

    renderHook(() => useTheme());

    for (const shade of SHADES) {
      expect(rootVar(`--tw-color-primary-${shade}`)).toBe('');
      expect(rootVar(`--tw-color-secondary-${shade}`)).toBe('');
    }
  });

  it('cai no tema padrão (verde) quando o nome não existe', () => {
    vi.stubEnv('VITE_THEME_COLOR', 'tema-inexistente');

    const { result } = renderHook(() => useTheme());

    expect(result.current.currentTheme.name).toBe('verde');
    expect(rootVar('--primary-color')).toBe('#22c55e');
    expect(rootVar('--vx-primary-500')).toBe('34 197 94');
  });

  it('usa o tema padrão (verde) quando VITE_THEME_COLOR está vazio', () => {
    vi.stubEnv('VITE_THEME_COLOR', '');

    const { result } = renderHook(() => useTheme());

    expect(result.current.themeName).toBe('verde');
    expect(rootVar('--vx-primary-600')).toBe('22 163 74');
  });
});
