// Hand-off de sessão: o app de login (frontend/vortex-authorization-service) volta para esta
// aplicação com a sessão na URL. Desta URL só sai o refresh token, que fica em memória e é trocado
// no backend do serviço de autorização (POST /api/auth/refresh). O que vai para o localStorage é a
// resposta do serviço, nunca o que veio na URL: um link forjado não grava access token, usuário nem
// papéis escolhidos por quem montou o link, e um refresh token inválido não grava nada.
//
// Formato principal: <página>#authData=<encodeURIComponent(JSON {accessToken, refreshToken, user})>.
// O fragmento não vai na requisição HTTP, então os tokens não chegam a log de servidor nem de proxy.
// Formato antigo, aceito por compatibilidade: <página>?authData=<o mesmo valor>.
// Formato alternativo legado: <página>#access_token=...&refresh_token=...&user=<JSON>.
//
// O valor é codificado uma vez só no app de login, então é decodificado uma vez só aqui:
// URLSearchParams.get já faz o percent-decoding. Um segundo decodeURIComponent derrubava (URIError)
// ou corrompia qualquer '%' literal do payload.
//
// handleAuthCallback precisa rodar antes de o router existir (ver authCallbackEntry.ts): o
// createWebHistory guarda a URL corrente quando é criado e a regrava na primeira navegação. Por isso
// a limpeza da URL é síncrona; a troca é assíncrona e o guard do router espera por ela com
// awaitAuthHandOff().

import { requestSessionRefresh, type LoginResponse } from '@/api/authHttp'

/** Formato do hand-off que o app de login monta (buildAuthRedirectUrl). Só o refreshToken é lido. */
export interface AuthCallbackData {
  accessToken: string
  refreshToken: string
  user: LoginResponse['user']
}

const AUTH_DATA_PARAM = 'authData'
const LEGACY_HASH_PARAMS = ['access_token', 'refresh_token', 'user'] as const
const HAND_OFF_HASH_PARAMS = [AUTH_DATA_PARAM, ...LEGACY_HASH_PARAMS]

// Troca em andamento (ou terminada) do último hand-off desta carga de página
let pendingHandOff: Promise<boolean> | null = null

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0
}

// Tira da URL só os parâmetros do hand-off (o resto da query e do fragmento fica), sem criar
// entrada no histórico: o botão Voltar não pode levar de novo a uma URL com tokens.
function removeHandOffFromUrl(url: URL, hashParams: URLSearchParams): void {
  if (url.searchParams.has(AUTH_DATA_PARAM)) {
    url.searchParams.delete(AUTH_DATA_PARAM)
  }
  if (HAND_OFF_HASH_PARAMS.some((key) => hashParams.has(key))) {
    for (const key of HAND_OFF_HASH_PARAMS) {
      hashParams.delete(key)
    }
    url.hash = hashParams.toString()
  }
  window.history.replaceState(window.history.state, document.title, url.toString())
}

// Há hand-off na URL? (qualquer um dos formatos, mesmo quebrado: a URL é limpa de todo jeito)
function hasHandOff(url: URL, hashParams: URLSearchParams): boolean {
  return url.searchParams.has(AUTH_DATA_PARAM) || HAND_OFF_HASH_PARAMS.some((key) => hashParams.has(key))
}

// Refresh token do hand-off, já decodificado uma vez (lança quando o hand-off está quebrado ou sem ele)
function readRefreshToken(url: URL, hashParams: URLSearchParams): string {
  const encoded = hashParams.get(AUTH_DATA_PARAM) ?? url.searchParams.get(AUTH_DATA_PARAM)
  const refreshToken: unknown =
    encoded === null
      ? hashParams.get('refresh_token')
      : (JSON.parse(encoded) as { refreshToken?: unknown } | null)?.refreshToken
  if (!isNonEmptyString(refreshToken)) {
    throw new Error('Incomplete auth hand-off: refreshToken is required')
  }
  return refreshToken
}

// Só grava uma sessão completa vinda do serviço: nada de "undefined" no localStorage
function isCompleteSession(session: LoginResponse): boolean {
  return (
    isNonEmptyString(session.accessToken) &&
    isNonEmptyString(session.refreshToken) &&
    typeof session.user === 'object' &&
    session.user !== null
  )
}

async function exchangeHandOff(refreshToken: string): Promise<boolean> {
  try {
    const session = await requestSessionRefresh(refreshToken)
    if (!isCompleteSession(session)) {
      throw new Error('Incomplete session returned by the auth service')
    }

    localStorage.setItem('accessToken', session.accessToken)
    localStorage.setItem('refreshToken', session.refreshToken)
    localStorage.setItem('vortex_user', JSON.stringify(session.user))

    console.log('[Auth Callback] Session exchanged and stored successfully')
    return true
  } catch (error) {
    console.error('[Auth Callback] Auth service refused the hand-off:', error)
    return false
  }
}

/**
 * Consome o hand-off da URL: limpa a URL na hora e começa a troca do refresh token no serviço.
 * Devolve true quando começou uma troca; o resultado dela sai de awaitAuthHandOff().
 */
export function handleAuthCallback(): boolean {
  try {
    const url = new URL(window.location.href)
    const hashParams = new URLSearchParams(url.hash.slice(1))
    if (!hasHandOff(url, hashParams)) {
      return false
    }

    let refreshToken: string
    try {
      refreshToken = readRefreshToken(url, hashParams)
    } finally {
      // Logo depois de ler e antes de qualquer outra coisa: nem um payload inválido deixa token na URL
      removeHandOffFromUrl(url, hashParams)
    }

    pendingHandOff = exchangeHandOff(refreshToken)
    return true
  } catch (error) {
    console.error('[Auth Callback] Error processing auth callback:', error)
    return false
  }
}

/** Espera a troca do hand-off desta carga de página; false sem hand-off ou quando o serviço recusou. Nunca rejeita. */
export function awaitAuthHandOff(): Promise<boolean> {
  return pendingHandOff ?? Promise.resolve(false)
}
