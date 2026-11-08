// Primeiro import de main.ts: consome o hand-off de sessão do app de login (#authData=...) antes
// de qualquer outro módulo rodar. O router (createWebHistory) guarda a URL corrente quando é
// criado, ao avaliar ./router, e a regrava com replaceState na primeira navegação. Com o callback
// chamado depois disso, os tokens voltavam para a barra de endereço e para o histórico.
import { handleAuthCallback } from './utils/authCallback'

handleAuthCallback()
