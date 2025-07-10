// Auth callback handler for when users return from the auth service
// This should be called when the auth service redirects back to the main application

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

export function handleAuthCallback(): boolean {
  try {
    // Check if we're returning from auth service with auth data
    const urlParams = new URLSearchParams(window.location.search);
    const authDataParam = urlParams.get('authData');
    
    if (authDataParam) {
      // Decode and parse auth data
      const authData: AuthCallbackData = JSON.parse(decodeURIComponent(authDataParam));
      
      // Store tokens and user data
      localStorage.setItem('accessToken', authData.accessToken);
      localStorage.setItem('refreshToken', authData.refreshToken);
      localStorage.setItem('vortex_user', JSON.stringify(authData.user));
      
      // Clean up URL
      const cleanUrl = window.location.origin + window.location.pathname;
      window.history.replaceState({}, document.title, cleanUrl);
      
      console.log('[Auth Callback] Auth data stored successfully');
      return true;
    }
    
    // Check for tokens in URL hash (alternative method)
    const hash = window.location.hash.substring(1);
    const hashParams = new URLSearchParams(hash);
    const accessToken = hashParams.get('access_token');
    const refreshToken = hashParams.get('refresh_token');
    const userParam = hashParams.get('user');
    
    if (accessToken && refreshToken && userParam) {
      const user = JSON.parse(decodeURIComponent(userParam));
      
      // Store tokens and user data
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);
      localStorage.setItem('vortex_user', JSON.stringify(user));
      
      // Clean up URL
      window.location.hash = '';
      
      console.log('[Auth Callback] Auth data from hash stored successfully');
      return true;
    }
    
    return false;
  } catch (error) {
    console.error('[Auth Callback] Error processing auth callback:', error);
    return false;
  }
}

export function getReturnUrl(): string {
  const returnUrl = sessionStorage.getItem('vortex_return_url');
  sessionStorage.removeItem('vortex_return_url');
  return returnUrl || '/';
}