# Sistema de Estoque - Frontend

Frontend desenvolvido em Vue.js 3 com TypeScript para o sistema de controle de estoque da Vortex.

## Tecnologias Utilizadas

- **Framework**: Vue.js 3 + TypeScript
- **UI Library**: Vuetify 3
- **Gerenciamento de Estado**: Pinia
- **Cliente HTTP**: Axios
- **Gráficos**: Chart.js + vue-chartjs
- **Validação**: vee-validate + yup
- **Testes**: Vitest (unitários) + Playwright (E2E)
- **Build Tool**: Vite

## Pré-requisitos

- Node.js 18+ 
- npm 9+
- Backend rodando em http://localhost:8080

## Instalação

1. Clone o repositório e acesse a pasta do frontend:
```bash
cd frontend
```

2. Instale as dependências:
```bash
npm install
```

3. Configure as variáveis de ambiente:
```bash
# Crie um arquivo .env na raiz do frontend
echo "VITE_API_BASE_URL=http://localhost:8080" > .env
```

## Geração do Client de API

O client de API é gerado automaticamente a partir do OpenAPI/Swagger do backend:

```bash
npm run generate-api
```

Este comando irá:
- Ler o arquivo `docs/openapi-backend.json`
- Gerar os tipos TypeScript e clients Axios em `src/api/generated/`
- Os arquivos gerados não devem ser editados manualmente

## Desenvolvimento

Para rodar o projeto em modo de desenvolvimento:

```bash
npm run dev
```

O frontend estará disponível em http://localhost:5173

### Scripts Disponíveis

- `npm run dev` - Inicia o servidor de desenvolvimento
- `npm run build` - Gera a build de produção
- `npm run preview` - Visualiza a build de produção
- `npm run test:unit` - Executa os testes unitários
- `npm run test:e2e` - Executa os testes E2E
- `npm run lint` - Executa o linter
- `npm run format` - Formata o código
- `npm run type-check` - Verifica os tipos TypeScript
- `npm run generate-api` - Gera o client de API

## Estrutura do Projeto

```
frontend/
├── src/
│   ├── api/              # Configuração e clients de API
│   │   ├── generated/    # Client gerado automaticamente
│   │   ├── config.ts     # Configuração do Axios
│   │   └── index.ts      # Exportações da API
│   ├── assets/           # Arquivos estáticos
│   ├── components/       # Componentes reutilizáveis
│   ├── router/           # Configuração de rotas
│   ├── stores/           # Stores Pinia
│   │   ├── theme.ts      # Store de tema
│   │   ├── productType.ts # Store de tipos de produto
│   │   ├── product.ts    # Store de produtos
│   │   ├── movement.ts   # Store de movimentos
│   │   └── report.ts     # Store de relatórios
│   ├── views/            # Páginas da aplicação
│   │   ├── HomeView.vue  # Dashboard
│   │   ├── ProductTypesView.vue # CRUD de tipos
│   │   ├── ProductsView.vue     # CRUD de produtos
│   │   ├── MovementsView.vue    # CRUD de movimentos
│   │   └── ReportsView.vue      # Relatórios
│   ├── App.vue           # Componente raiz
│   └── main.ts           # Entrada da aplicação
├── e2e/                  # Testes E2E
├── public/               # Arquivos públicos
└── package.json          # Dependências
```

## Funcionalidades

### 1. Dashboard (Home)
- Estatísticas gerais do sistema
- Produtos com baixo estoque
- Movimentos recentes

### 2. Tipos de Produto
- Listagem com busca e paginação
- Criação, edição e exclusão
- Validação de formulários

### 3. Produtos
- CRUD completo
- Associação com tipos de produto
- Controle de estoque
- Filtros e busca

### 4. Movimentação
- Registro de entradas e saídas
- Cálculo automático do valor de venda
- Atualização automática do estoque
- Filtros por tipo de movimento

### 5. Relatórios
- Produtos por tipo (gráfico de barras)
- Lucro por produto (gráfico de pizza)
- Estatísticas de receita, custo e margem

### 6. Tema
- Modo claro e escuro
- Persistência da escolha em localStorage
- Cores personalizadas da marca

## Testes

### Testes Unitários

```bash
npm run test:unit
```

Os testes unitários cobrem:
- Stores Pinia
- Lógica de negócio
- Componentes isolados

### Testes E2E

```bash
npm run test:e2e
```

Os testes E2E verificam:
- Navegação entre páginas
- Funcionalidade de tema
- Interações do usuário
- Fluxos completos

## Build de Produção

Para gerar a build de produção:

```bash
npm run build
```

Os arquivos serão gerados em `dist/`. Para visualizar:

```bash
npm run preview
```

## Observações

- O frontend espera que o backend esteja rodando em http://localhost:8080
- Todas as chamadas à API incluem tratamento de erro
- O tema persiste entre sessões
- A aplicação é totalmente responsiva
- Os gráficos são interativos e atualizados em tempo real
