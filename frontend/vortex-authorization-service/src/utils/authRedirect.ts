import type { User } from '@/types/auth';

// Aplicação principal (Vue) quando a sessão não guardou outra URL de retorno
export const DEFAULT_RETURN_URL = 'http://localhost:5173';

export interface AuthRedirectData {
  accessToken: string;
  refreshToken: string;
  user: User;
}

// Para onde voltar depois do login ou do cadastro
export function getReturnUrl(): string {
  return sessionStorage.getItem('vortex_return_url') || DEFAULT_RETURN_URL;
}

// URL de hand-off para a aplicação Vue: <returnUrl>?authData=<JSON {accessToken, refreshToken, user}
// codificado com encodeURIComponent>. É contrato com handleAuthCallback() em
// frontend/vortex-application-service/src/utils/authCallback.ts, que lê o parâmetro `authData` da
// query string: não mudar o nome do parâmetro, os nomes das chaves nem a codificação.
export function buildAuthRedirectUrl(
  returnUrl: string,
  { accessToken, refreshToken, user }: AuthRedirectData
): string {
  const authData = encodeURIComponent(JSON.stringify({
    accessToken,
    refreshToken,
    user
  }));

  return `${returnUrl}?authData=${authData}`;
}
