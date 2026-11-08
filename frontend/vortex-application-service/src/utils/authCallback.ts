// Hand-off de sessão: o app de login (frontend/vortex-authorization-service) volta para esta
// aplicação com os tokens na URL, e handleAuthCallback os grava no localStorage.
//
// Formato principal: <página>#authData=<encodeURIComponent(JSON {accessToken, refreshToken, user})>.
// O fragmento não vai na requisição HTTP, então os tokens não chegam a log de servidor nem de proxy.
// Formato antigo, aceito por compatibilidade: <página>?authData=<o mesmo valor>.
// Formato alternativo legado: <página>#access_token=...&refresh_token=...&user=<JSON>.
//
// O valor é codificado uma vez só no app de login, então é decodificado uma vez só aqui:
// URLSearchParams.get já faz o percent-decoding. Um segundo decodeURIComponent derrubava (URIError)
// ou corrompia qualquer '%' literal do payload, como no e-mail ana%zz@vortex.dev.
//
// Precisa rodar antes de o router existir (ver authCallbackEntry.ts): o createWebHistory guarda a
// URL corrente quando é criado e a regrava na primeira navegação.

export interface AuthCallbackData {
  accessToken: string;
  refreshToken: string;
  user: {
    id: string;
    email: string;
    username: string;
    roles: string[];
    lastLogin?: string;
    isActive: boolean;
    isVerified: boolean;
  };
}

const AUTH_DATA_PARAM = 'authData';
const LEGACY_HASH_PARAMS = ['access_token', 'refresh_token', 'user'] as const;
const HAND_OFF_HASH_PARAMS = [AUTH_DATA_PARAM, ...LEGACY_HASH_PARAMS];

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0;
}

// Só grava um hand-off completo: nada de "undefined" no localStorage
function assertAuthCallbackData(value: unknown): asserts value is AuthCallbackData {
  const data = value as Partial<AuthCallbackData> | null;
  if (
    !data ||
    !isNonEmptyString(data.accessToken) ||
    !isNonEmptyString(data.refreshToken) ||
    typeof data.user !== 'object' ||
    data.user === null
  ) {
    throw new Error('Incomplete auth hand-off: accessToken, refreshToken and user are required');
  }
}

// Tira da URL só os parâmetros do hand-off (o resto da query e do fragmento fica), sem criar
// entrada no histórico: o botão Voltar não pode levar de novo a uma URL com tokens.
function removeHandOffFromUrl(url: URL, hashParams: URLSearchParams): void {
  if (url.searchParams.has(AUTH_DATA_PARAM)) {
    url.searchParams.delete(AUTH_DATA_PARAM);
  }
  if (HAND_OFF_HASH_PARAMS.some((key) => hashParams.has(key))) {
    for (const key of HAND_OFF_HASH_PARAMS) {
      hashParams.delete(key);
    }
    url.hash = hashParams.toString();
  }
  window.history.replaceState(window.history.state, document.title, url.toString());
}

// Lê o hand-off da URL, já decodificado uma vez (null quando não há hand-off)
function readHandOff(url: URL, hashParams: URLSearchParams): (() => AuthCallbackData) | null {
  const encoded = hashParams.get(AUTH_DATA_PARAM) ?? url.searchParams.get(AUTH_DATA_PARAM);
  if (encoded !== null) {
    return () => JSON.parse(encoded);
  }

  const accessToken = hashParams.get('access_token');
  const refreshToken = hashParams.get('refresh_token');
  const user = hashParams.get('user');
  if (accessToken && refreshToken && user) {
    return () => ({ accessToken, refreshToken, user: JSON.parse(user) });
  }

  return null;
}

export function handleAuthCallback(): boolean {
  try {
    const url = new URL(window.location.href);
    const hashParams = new URLSearchParams(url.hash.slice(1));

    const parseHandOff = readHandOff(url, hashParams);
    if (!parseHandOff) {
      return false;
    }

    // Logo depois de ler e antes de interpretar: nem um payload inválido deixa token na URL
    removeHandOffFromUrl(url, hashParams);

    const authData: unknown = parseHandOff();
    assertAuthCallbackData(authData);

    localStorage.setItem('accessToken', authData.accessToken);
    localStorage.setItem('refreshToken', authData.refreshToken);
    localStorage.setItem('vortex_user', JSON.stringify(authData.user));

    console.log('[Auth Callback] Auth data stored successfully');
    return true;
  } catch (error) {
    console.error('[Auth Callback] Error processing auth callback:', error);
    return false;
  }
}
