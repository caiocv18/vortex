import type { User } from '@/types/auth';

// Aplicação principal (Vue) quando o build não define VITE_MAIN_APP_URL
export const DEFAULT_MAIN_APP_URL = 'http://localhost:5173';

// Parâmetro de query com que a aplicação Vue diz para onde voltar: ela abre
// <este app>/login?returnUrl=<página atual> (buildAuthPageUrl em
// frontend/vortex-application-service/src/api/authService.ts)
export const RETURN_URL_PARAM = 'returnUrl';

// Cópia do ?returnUrl= aceito no sessionStorage DESTE app (origem própria): os links entre /login,
// /register e /forgot-password não levam a query adiante, e a cópia leva
export const RETURN_URL_STORAGE_KEY = 'vortex_return_url';

// Chave do hand-off no fragmento da URL de volta (contrato com handleAuthCallback, ver abaixo)
export const AUTH_DATA_PARAM = 'authData';

export interface AuthRedirectData {
  accessToken: string;
  refreshToken: string;
  user: User;
}

const withoutTrailingSlash = (url: string) => url.replace(/\/+$/, '');

function parseHttpUrl(value: string): URL | null {
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url : null;
  } catch {
    return null;
  }
}

// URL da aplicação principal: destino padrão do hand-off e única origem aceita como retorno.
// VITE_MAIN_APP_URL (variável de build) troca o padrão, por exemplo http://localhost:3000 no
// Docker ou http://localhost:4173 no vite preview; valor que não é URL http(s) é ignorado.
export function getMainAppUrl(): string {
  const configured = import.meta.env.VITE_MAIN_APP_URL?.trim();
  if (!configured) {
    return DEFAULT_MAIN_APP_URL;
  }
  if (!parseHttpUrl(configured)) {
    console.warn(
      `[authRedirect] VITE_MAIN_APP_URL não é uma URL http(s) (${configured}); usando ${DEFAULT_MAIN_APP_URL}`
    );
    return DEFAULT_MAIN_APP_URL;
  }
  return withoutTrailingSlash(configured);
}

// URL de retorno aceita, ou null. Só passa URL absoluta http(s) com a mesma origem (esquema, host
// e porta) da aplicação principal: este app entrega os tokens para quem estiver nessa URL, então
// qualquer outro destino seria um open redirect com sessão de brinde. O fragmento sai, porque é
// nele que o hand-off vai, e usuário e senha embutidos na URL também.
export function toAllowedReturnUrl(candidate: string | null | undefined): string | null {
  if (!candidate) {
    return null;
  }
  const url = parseHttpUrl(candidate);
  if (!url || url.origin !== new URL(getMainAppUrl()).origin) {
    return null;
  }
  url.hash = '';
  url.username = '';
  url.password = '';
  return url.href;
}

function readStoredReturnUrl(): string | null {
  try {
    return sessionStorage.getItem(RETURN_URL_STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStoredReturnUrl(value: string | null): void {
  try {
    if (value === null) {
      sessionStorage.removeItem(RETURN_URL_STORAGE_KEY);
    } else {
      sessionStorage.setItem(RETURN_URL_STORAGE_KEY, value);
    }
  } catch {
    // sessionStorage indisponível (armazenamento bloqueado): vale só o ?returnUrl= da URL atual
  }
}

// Roda na carga da página (main.tsx), antes do router. ?returnUrl= aceito fica guardado; um
// ?returnUrl= recusado apaga o guardado, para não voltar à página de uma visita anterior; sem o
// parâmetro (recarga, link de redefinição de senha), o guardado fica.
export function rememberReturnUrl(search: string = window.location.search): void {
  const params = new URLSearchParams(search);
  if (!params.has(RETURN_URL_PARAM)) {
    return;
  }
  writeStoredReturnUrl(toAllowedReturnUrl(params.get(RETURN_URL_PARAM)));
}

// Para onde voltar depois do login ou do cadastro, nesta ordem: o ?returnUrl= da URL atual, o
// guardado por rememberReturnUrl e a aplicação principal. Os dois primeiros passam pelo mesmo
// filtro de origem (toAllowedReturnUrl).
export function getReturnUrl(): string {
  const fromQuery = new URLSearchParams(window.location.search).get(RETURN_URL_PARAM);
  return toAllowedReturnUrl(fromQuery) ?? toAllowedReturnUrl(readStoredReturnUrl()) ?? getMainAppUrl();
}

// URL de hand-off para a aplicação Vue:
// <returnUrl sem fragmento>#authData=<encodeURIComponent(JSON {accessToken, refreshToken, user})>,
// codificado uma vez só. É contrato com handleAuthCallback() em
// frontend/vortex-application-service/src/utils/authCallback.ts, que lê `authData` do fragmento
// com URLSearchParams.get (que já decodifica, uma vez): não mudar o nome da chave, os nomes dos
// campos nem a codificação. Vai no fragmento, e não na query, porque o fragmento não sai do
// navegador: os tokens não chegam ao access log do servidor que entrega a aplicação nem ao Referer.
export function buildAuthRedirectUrl(
  returnUrl: string,
  { accessToken, refreshToken, user }: AuthRedirectData
): string {
  const authData = encodeURIComponent(JSON.stringify({
    accessToken,
    refreshToken,
    user
  }));

  const hashIndex = returnUrl.indexOf('#');
  const target = hashIndex === -1 ? returnUrl : returnUrl.slice(0, hashIndex);
  return `${target}#${AUTH_DATA_PARAM}=${authData}`;
}
