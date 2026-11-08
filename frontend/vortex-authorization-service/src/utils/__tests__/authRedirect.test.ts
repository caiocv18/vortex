import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  buildAuthRedirectUrl,
  DEFAULT_MAIN_APP_URL,
  getMainAppUrl,
  getReturnUrl,
  rememberReturnUrl,
  RETURN_URL_STORAGE_KEY,
} from '@/utils/authRedirect';
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
// frontend/vortex-application-service/src/utils/authCallback.ts (não importado: é outro módulo):
// `authData` do fragmento, decodificado uma vez só pelo URLSearchParams.get, sem decodeURIComponent
function parseLikeVueCallback(url: string) {
  const parsed = new URL(url);
  const hashParams = new URLSearchParams(parsed.hash.slice(1));
  const authDataParam = hashParams.get('authData');
  return {
    parsed,
    hashParams,
    authData: authDataParam !== null ? JSON.parse(authDataParam) : null,
  };
}

// O que o navegador manda ao servidor numa navegação: tudo menos o fragmento
function requestTarget(parsed: URL): string {
  return `${parsed.origin}${parsed.pathname}${parsed.search}`;
}

// A página atual deste app (o jsdom do Vitest roda em http://localhost:3000)
function visit(pathAndQuery: string): void {
  window.history.replaceState(null, '', pathAndQuery);
}

function loginPageWithReturnUrl(returnUrl: string): string {
  return `/login?returnUrl=${encodeURIComponent(returnUrl)}`;
}

describe('buildAuthRedirectUrl (hand-off para a aplicação Vue)', () => {
  it('gera exatamente <returnUrl>#authData=<JSON codificado uma vez> com accessToken, refreshToken e user', () => {
    expect(buildAuthRedirectUrl('http://localhost:5173', data)).toBe(
      'http://localhost:5173#authData=' +
        '%7B%22accessToken%22%3A%22eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3ZjFjIn0.c2ln%22' +
        '%2C%22refreshToken%22%3A%22refresh-123%22' +
        '%2C%22user%22%3A%7B%22id%22%3A%227f1c%22%2C%22email%22%3A%22ana%2Bteste%40vortex.dev%22' +
        '%2C%22username%22%3A%22ana%22%2C%22roles%22%3A%5B%22USER%22%5D' +
        '%2C%22isActive%22%3Atrue%2C%22isVerified%22%3Afalse%7D%7D'
    );
  });

  it('é lido de volta pelo callback da aplicação Vue sem perda, com nada na query string', () => {
    const { parsed, hashParams, authData } = parseLikeVueCallback(
      buildAuthRedirectUrl('http://localhost:5173', data)
    );

    expect(parsed.origin).toBe('http://localhost:5173');
    expect(parsed.search).toBe('');
    expect([...hashParams.keys()]).toEqual(['authData']);
    expect(authData).toEqual(data);
    expect(Object.keys(authData)).toEqual(['accessToken', 'refreshToken', 'user']);
  });

  it('não põe os tokens no que o navegador manda ao servidor (request line e Referer)', () => {
    const { parsed } = parseLikeVueCallback(
      buildAuthRedirectUrl('http://localhost:5173/movements?tab=2', data)
    );

    expect(requestTarget(parsed)).toBe('http://localhost:5173/movements?tab=2');
    expect(requestTarget(parsed)).not.toContain(data.refreshToken);
    expect(requestTarget(parsed)).not.toContain(encodeURIComponent(data.accessToken));
  });

  it.each(['ana%zz@vortex.dev', 'ana%41@x.com', 'ana%25@x.com', 'ana+teste@vortex.dev'])(
    'não perde o e-mail %s (o sinal de porcentagem literal sobrevive a uma decodificação só)',
    (email) => {
      const withEmail = { ...data, user: { ...user, email } };

      const { authData } = parseLikeVueCallback(buildAuthRedirectUrl('http://localhost:5173', withEmail));

      expect(authData).toEqual(withEmail);
    }
  );

  it('decodificar duas vezes, como o callback antigo, quebraria com um sinal de porcentagem literal: o contrato é uma vez', () => {
    const withEmail = { ...data, user: { ...user, email: 'ana%zz@vortex.dev' } };
    const { hashParams } = parseLikeVueCallback(buildAuthRedirectUrl('http://localhost:5173', withEmail));

    expect(() => decodeURIComponent(hashParams.get('authData')!)).toThrow(URIError);
  });

  it('mantém caminho e query da URL de retorno e troca um fragmento que ela já tinha', () => {
    const url = buildAuthRedirectUrl('http://localhost:5173/movements?tab=2#secao', data);
    const { parsed, hashParams, authData } = parseLikeVueCallback(url);

    expect(url.startsWith('http://localhost:5173/movements?tab=2#authData=')).toBe(true);
    expect(parsed.searchParams.get('tab')).toBe('2');
    expect([...hashParams.keys()]).toEqual(['authData']);
    expect(authData).toEqual(data);
  });
});

describe('getReturnUrl e rememberReturnUrl (?returnUrl= da aplicação Vue)', () => {
  afterEach(() => {
    visit('/');
    sessionStorage.clear();
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it('usa a aplicação Vue na porta 5173 quando não há ?returnUrl= nem URL guardada', () => {
    expect(DEFAULT_MAIN_APP_URL).toBe('http://localhost:5173');
    expect(getMainAppUrl()).toBe('http://localhost:5173');
    expect(getReturnUrl()).toBe('http://localhost:5173');
  });

  it('volta para o ?returnUrl= da mesma origem da aplicação Vue, com caminho e query', () => {
    visit(loginPageWithReturnUrl('http://localhost:5173/movements?tab=2&q=50%25 off'));

    expect(getReturnUrl()).toBe('http://localhost:5173/movements?tab=2&q=50%25%20off');
  });

  it.each([
    'https://evil.example/roubar',
    'http://localhost:5173.evil.example/',
    'http://localhost:5173@evil.example/',
    '//evil.example/roubar',
    '/movements',
    'javascript:alert(document.cookie)',
    'http://localhost:5174/',
    'https://localhost:5173/',
    'http://127.0.0.1:5173/',
    'http://localhost:3001/login',
    '',
  ])('ignora ?returnUrl=%s (outra origem ou URL inválida: sem open redirect)', (candidate) => {
    visit(loginPageWithReturnUrl(candidate));
    rememberReturnUrl();

    expect(getReturnUrl()).toBe('http://localhost:5173');
    expect(sessionStorage.getItem(RETURN_URL_STORAGE_KEY)).toBeNull();
  });

  it('tira do retorno o fragmento e usuário e senha embutidos', () => {
    visit(loginPageWithReturnUrl('http://ana:segredo@localhost:5173/movements#authData=antigo'));

    expect(getReturnUrl()).toBe('http://localhost:5173/movements');
  });

  it('VITE_MAIN_APP_URL troca o destino padrão e a origem aceita', () => {
    vi.stubEnv('VITE_MAIN_APP_URL', 'http://localhost:3000/');

    expect(getMainAppUrl()).toBe('http://localhost:3000');
    expect(getReturnUrl()).toBe('http://localhost:3000');

    visit(loginPageWithReturnUrl('http://localhost:3000/products'));
    expect(getReturnUrl()).toBe('http://localhost:3000/products');

    visit(loginPageWithReturnUrl('http://localhost:5173/products'));
    expect(getReturnUrl()).toBe('http://localhost:3000');
  });

  it('ignora VITE_MAIN_APP_URL que não é URL http(s) e fica com a porta 5173', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.stubEnv('VITE_MAIN_APP_URL', 'localhost:3000');

    expect(getMainAppUrl()).toBe('http://localhost:5173');
    expect(warn).toHaveBeenCalled();
  });

  it('guarda o ?returnUrl= aceito para quando a navegação interna perde a query', () => {
    visit(loginPageWithReturnUrl('http://localhost:5173/movements'));
    rememberReturnUrl();

    // Link "Create one here": /register sem a query
    visit('/register');

    expect(sessionStorage.getItem(RETURN_URL_STORAGE_KEY)).toBe('http://localhost:5173/movements');
    expect(getReturnUrl()).toBe('http://localhost:5173/movements');
  });

  it('um ?returnUrl= recusado apaga a URL guardada numa visita anterior', () => {
    sessionStorage.setItem(RETURN_URL_STORAGE_KEY, 'http://localhost:5173/movements');

    visit(loginPageWithReturnUrl('https://evil.example/'));
    rememberReturnUrl();
    visit('/login');

    expect(sessionStorage.getItem(RETURN_URL_STORAGE_KEY)).toBeNull();
    expect(getReturnUrl()).toBe('http://localhost:5173');
  });

  it('sem ?returnUrl= (recarga, link de redefinição de senha) mantém a URL guardada', () => {
    sessionStorage.setItem(RETURN_URL_STORAGE_KEY, 'http://localhost:5173/movements');

    visit('/reset-password/abc');
    rememberReturnUrl();

    expect(getReturnUrl()).toBe('http://localhost:5173/movements');
  });

  it('a URL guardada passa pelo mesmo filtro de origem', () => {
    sessionStorage.setItem(RETURN_URL_STORAGE_KEY, 'https://evil.example/roubar');

    expect(getReturnUrl()).toBe('http://localhost:5173');
  });

  it('o ?returnUrl= da página atual vence a URL guardada', () => {
    sessionStorage.setItem(RETURN_URL_STORAGE_KEY, 'http://localhost:5173/movements');

    visit(loginPageWithReturnUrl('http://localhost:5173/products'));

    expect(getReturnUrl()).toBe('http://localhost:5173/products');
  });
});
