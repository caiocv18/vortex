import { afterEach, describe, expect, it } from 'vitest';
import { buildAuthRedirectUrl, getReturnUrl, DEFAULT_RETURN_URL } from '@/utils/authRedirect';
import type { User } from '@/types/auth';

const user: User = {
  id: '7f1c',
  email: 'ana+teste@vortex.dev',
  username: 'ana',
  roles: ['USER'],
  isActive: true,
  isVerified: false,
};

const data = {
  accessToken: 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3ZjFjIn0.c2ln',
  refreshToken: 'refresh-123',
  user,
};

// Mesma leitura que handleAuthCallback() faz em
// frontend/vortex-application-service/src/utils/authCallback.ts (não importado: é outro módulo)
function parseLikeVueCallback(url: string) {
  const parsed = new URL(url);
  const authDataParam = parsed.searchParams.get('authData');
  return {
    parsed,
    authData: authDataParam ? JSON.parse(decodeURIComponent(authDataParam)) : null,
  };
}

describe('buildAuthRedirectUrl (hand-off para a aplicação Vue)', () => {
  it('gera exatamente <returnUrl>?authData=<JSON codificado> com accessToken, refreshToken e user', () => {
    expect(buildAuthRedirectUrl('http://localhost:5173', data)).toBe(
      'http://localhost:5173?authData=' +
        '%7B%22accessToken%22%3A%22eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3ZjFjIn0.c2ln%22' +
        '%2C%22refreshToken%22%3A%22refresh-123%22' +
        '%2C%22user%22%3A%7B%22id%22%3A%227f1c%22%2C%22email%22%3A%22ana%2Bteste%40vortex.dev%22' +
        '%2C%22username%22%3A%22ana%22%2C%22roles%22%3A%5B%22USER%22%5D' +
        '%2C%22isActive%22%3Atrue%2C%22isVerified%22%3Afalse%7D%7D'
    );
  });

  it('é lido de volta pelo callback da aplicação Vue sem perda (inclusive o + do e-mail)', () => {
    const { parsed, authData } = parseLikeVueCallback(buildAuthRedirectUrl('http://localhost:5173', data));

    expect(parsed.origin).toBe('http://localhost:5173');
    expect([...parsed.searchParams.keys()]).toEqual(['authData']);
    expect(parsed.hash).toBe('');
    expect(authData).toEqual(data);
    expect(Object.keys(authData)).toEqual(['accessToken', 'refreshToken', 'user']);
  });

  it('respeita o caminho da URL de retorno', () => {
    const url = buildAuthRedirectUrl('http://localhost:5173/dashboard', data);

    expect(url.startsWith('http://localhost:5173/dashboard?authData=')).toBe(true);
    expect(parseLikeVueCallback(url).authData).toEqual(data);
  });
});

describe('getReturnUrl', () => {
  afterEach(() => {
    sessionStorage.clear();
  });

  it('usa a aplicação Vue na porta 5173 quando a sessão não guardou URL', () => {
    expect(DEFAULT_RETURN_URL).toBe('http://localhost:5173');
    expect(getReturnUrl()).toBe('http://localhost:5173');
  });

  it('usa vortex_return_url do sessionStorage quando existe', () => {
    sessionStorage.setItem('vortex_return_url', 'http://localhost:5173/produtos');

    expect(getReturnUrl()).toBe('http://localhost:5173/produtos');
  });
});
