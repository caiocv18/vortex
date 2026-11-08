import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.tsx'
import { rememberReturnUrl } from '@/utils/authRedirect'
import './index.css'

// Antes do router: o ?returnUrl= que a aplicação Vue mandou precisa sobreviver à navegação
// interna (login, cadastro, esqueci a senha), que não leva a query adiante
rememberReturnUrl()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
