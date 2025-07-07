# VORTEX Auth Frontend

Frontend React + Vite para o microsserviço de autenticação VORTEX.

## Funcionalidades Implementadas

### ✅ Interface de Autenticação
- **Login**: Email e senha com validação
- **Cadastro**: Formulário completo com validações
- **Recuperação de Senha**: Fluxo completo com email
- **Redefinição de Senha**: Via token recebido por email

### ✅ OAuth Integration
- **Google**: Redirecionamento para autenticação OAuth
- **GitHub**: Redirecionamento para autenticação OAuth

### ✅ Design System VORTEX
- **Cores**: Seguindo paleta do sistema principal
- **Componentes**: Botões, inputs e cards padronizados
- **Responsivo**: Funciona em desktop e mobile
- **Dark Mode**: Suporte automático baseado no sistema

### ✅ Experiência do Usuário
- **Loading States**: Indicadores visuais durante requisições
- **Validação de Formulários**: React Hook Form + validações
- **Notificações**: Toast para feedback ao usuário
- **Navegação**: React Router com rotas protegidas

### ✅ Segurança
- **Token Management**: Armazenamento seguro no localStorage
- **Auto-logout**: Em caso de token expirado
- **Validação**: Validações robustas nos formulários
- **HTTPS Ready**: Preparado para produção

## Como Executar

### Pré-requisitos
- Node.js 18+
- npm 9+
- Backend VORTEX Auth rodando em http://localhost:8081

### 1. Instalar Dependências
```bash
npm install
```

### 2. Executar em Desenvolvimento
```bash
npm run dev
```

A aplicação estará disponível em: http://localhost:5173

### 3. Build para Produção
```bash
npm run build
npm run preview
```

## Estrutura do Projeto

```
src/
├── hooks/
│   └── useAuth.tsx          # Context e hook de autenticação
├── pages/
│   ├── LoginPage.tsx        # Tela de login
│   ├── RegisterPage.tsx     # Tela de cadastro
│   ├── ForgotPasswordPage.tsx # Recuperação de senha
│   ├── ResetPasswordPage.tsx  # Redefinir senha
│   └── DashboardPage.tsx    # Dashboard pós-login
├── services/
│   ├── apiClient.ts         # Cliente HTTP com interceptors
│   └── authService.ts       # Serviços de autenticação
├── styles/
│   └── globals.css          # Estilos globais VORTEX
└── App.tsx                  # Componente principal
```

## Principais Dependências

### Produção
- **react**: ^18.2.0 - Framework principal
- **react-router-dom**: ^6.8.0 - Roteamento
- **axios**: ^1.3.0 - Cliente HTTP
- **react-hook-form**: ^7.43.0 - Gerenciamento de formulários
- **react-hot-toast**: ^2.4.0 - Notificações

### Desenvolvimento
- **vite**: ^4.1.0 - Build tool
- **typescript**: ~5.8.3 - Tipagem
- **@vitejs/plugin-react**: ^3.1.0 - Plugin React para Vite

## Configuração da API

Por padrão, a aplicação conecta com o backend em:
```
http://localhost:8081
```

Para alterar, edite o arquivo `src/services/apiClient.ts`:

```typescript
const API_BASE_URL = 'https://your-api-domain.com';
```

## Autenticação Local

### Login
```typescript
// Endpoint: POST /auth/login
{
  "email": "user@example.com",
  "password": "password123"
}
```

### Cadastro
```typescript
// Endpoint: POST /auth/register
{
  "email": "user@example.com",
  "name": "João Silva",
  "password": "Password123!",
  "confirmPassword": "Password123!"
}
```

### Recuperação de Senha
```typescript
// Endpoint: POST /auth/forgot-password
{
  "email": "user@example.com"
}
```

### Redefinir Senha
```typescript
// Endpoint: POST /auth/reset-password
{
  "token": "reset-token-from-email",
  "newPassword": "NewPassword123!",
  "confirmPassword": "NewPassword123!"
}
```

## OAuth Integration

### Google
```typescript
// Redireciona para: GET /auth/login/google
window.location.href = 'http://localhost:8081/auth/login/google';
```

### GitHub
```typescript
// Redireciona para: GET /auth/login/github
window.location.href = 'http://localhost:8081/auth/login/github';
```

## Gerenciamento de Estado

### Hook useAuth
```typescript
const { user, loading, login, register, logout, isAuthenticated } = useAuth();

// Login
await login({ email: 'user@example.com', password: 'password' });

// Cadastro
await register({ 
  email: 'user@example.com', 
  name: 'João Silva',
  password: 'password',
  confirmPassword: 'password'
});

// Logout
logout();
```

### Context AuthProvider
```typescript
<AuthProvider>
  <App />
</AuthProvider>
```

## Validações de Formulário

### Senhas
- Mínimo 8 caracteres
- Pelo menos 1 letra minúscula
- Pelo menos 1 letra maiúscula
- Pelo menos 1 número
- Pelo menos 1 caractere especial (@#$%^&+=)

### Email
- Formato válido de email
- Campo obrigatório

### Nome
- Mínimo 2 caracteres
- Máximo 100 caracteres
- Campo obrigatório

## Design System

### Cores Principais
```css
--vt-c-green: hsla(160, 100%, 37%, 1)      /* Cor principal */
--vt-c-green-hover: hsla(160, 100%, 37%, 0.2) /* Hover states */
--color-background: #ffffff                 /* Fundo claro */
--color-text: #181818                      /* Texto claro */
```

### Componentes
- **btn-primary**: Botão principal verde
- **btn-secondary**: Botão secundário com borda
- **btn-link**: Link estilizado
- **form-input**: Input padronizado
- **login-card**: Card de autenticação

### Responsividade
- **Desktop**: Layout otimizado para telas grandes
- **Mobile**: Layout adaptado para dispositivos móveis
- **Breakpoint**: 480px para mobile

## Deploy para Produção

### 1. Build
```bash
npm run build
```

### 2. Configurar Servidor Web
A pasta `dist/` contém os arquivos estáticos. Configure seu servidor web (Nginx, Apache, etc.) para servir estes arquivos.

### 3. Configurar HTTPS
Para produção, sempre use HTTPS:
```typescript
const API_BASE_URL = 'https://auth.yourdomain.com';
```

### 4. Configurar CORS
Certifique-se de que o backend aceita requests do domínio de produção.

## Troubleshooting

### Erro de CORS
- Verifique se o backend está configurado para aceitar o domínio do frontend
- Verifique se as portas estão corretas

### Token Expirado
- A aplicação redireciona automaticamente para login
- Tokens JWT têm validade de 8 horas

### Erro de Conexão
- Verifique se o backend está rodando
- Verifique a URL da API em `apiClient.ts`

## Scripts Disponíveis

```bash
npm run dev          # Servidor de desenvolvimento
npm run build        # Build para produção
npm run preview      # Preview do build
npm run lint         # Verificar código
```

---

# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Babel](https://babeljs.io/) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh