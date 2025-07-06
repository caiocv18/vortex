# Testes E2E - Sistema de Estoque Vortex

Este diretório contém todos os testes end-to-end (E2E) para a aplicação Sistema de Estoque Vortex, implementados usando Playwright.

## Estrutura dos Testes

### Arquivos de Teste

1. **`vue.spec.ts`** - Testes da página inicial (Home)
   - Verifica o carregamento da página principal
   - Testa o card de boas-vindas
   - Valida o teste de conexão com a API
   - Testa navegação através dos cards da home

2. **`navigation.spec.ts`** - Testes de navegação geral
   - Navegação via sidebar em todas as páginas
   - Funcionamento do toggle de tema
   - Comportamento do sidebar rail mode
   - Atualização correta dos títulos das páginas

3. **`product-types.spec.ts`** - Testes da página de Tipos de Produto
   - CRUD completo de tipos de produto
   - Validação de formulários
   - Funcionalidade de pesquisa
   - Abertura e fechamento de diálogos

4. **`products.spec.ts`** - Testes da página de Produtos
   - CRUD completo de produtos
   - Validação de formulários com campos obrigatórios
   - Exibição de valores monetários formatados
   - Indicadores visuais de estoque (chips coloridos)

5. **`movements.spec.ts`** - Testes da página de Movimentação
   - CRUD de movimentações de estoque
   - Filtros por tipo de movimentação (Entrada/Saída)
   - Validação de quantidades
   - Formatação de datas e valores

6. **`reports.spec.ts`** - Testes da página de Relatórios
   - Exibição de cards de estatísticas
   - Carregamento de gráficos
   - Formatação de valores monetários e percentuais
   - Responsividade dos gráficos

7. **`integration.spec.ts`** - Testes de integração
   - Fluxo completo de navegação
   - Funcionamento do tema em todas as páginas
   - Testes de responsividade
   - Verificação de carregamento de dados

8. **`accessibility.spec.ts`** - Testes de acessibilidade
   - Estrutura correta de cabeçalhos
   - Tempo de carregamento das páginas
   - Design responsivo em diferentes viewports

## Como Executar os Testes

### Pré-requisitos

1. Certifique-se de que o backend está rodando na porta 8080
2. Instale as dependências do frontend: `npm install`
3. Instale os browsers do Playwright: `npx playwright install`

### Comandos de Execução

```bash
# Executar todos os testes E2E
npm run test:e2e

# Executar testes em modo headless (padrão)
npx playwright test

# Executar testes com interface gráfica
npx playwright test --ui

# Executar um arquivo específico de testes
npx playwright test e2e/navigation.spec.ts

# Executar testes em um browser específico
npx playwright test --project=chromium

# Executar testes em modo debug
npx playwright test --debug
```

### Relatórios

Após a execução, os relatórios são gerados em:
- `playwright-report/` - Relatório HTML interativo
- `test-results/` - Screenshots e vídeos de falhas

Para visualizar o relatório:
```bash
npx playwright show-report
```

## Configuração

A configuração dos testes está em `playwright.config.ts`:

- **Timeout**: 30 segundos por teste
- **Browsers**: Chromium, Firefox, WebKit
- **Base URL**: http://localhost:5173 (dev) / http://localhost:4173 (preview)
- **Retry**: 2 tentativas em CI
- **Traces**: Capturados em caso de falha

## Estrutura dos Testes

### Padrões Utilizados

1. **Page Object Model**: Não implementado explicitamente, mas cada teste foca em uma página específica
2. **Seletores Robustos**: Uso de seletores baseados em texto e roles quando possível
3. **Waits Explícitos**: Aguardar elementos específicos ao invés de timeouts fixos
4. **Isolamento**: Cada teste é independente e pode ser executado isoladamente

### Boas Práticas Implementadas

- ✅ Testes organizados por funcionalidade
- ✅ Uso de `beforeEach` para setup comum
- ✅ Verificação de elementos visíveis antes da interação
- ✅ Timeouts apropriados para carregamento de dados
- ✅ Testes de responsividade
- ✅ Validação de acessibilidade básica
- ✅ Testes de integração end-to-end

## Manutenção

### Atualizando Testes

Quando a interface da aplicação mudar:

1. **Seletores**: Atualize os seletores se elementos mudarem
2. **Textos**: Ajuste textos esperados se labels mudarem
3. **Fluxos**: Modifique fluxos de teste se a navegação mudar
4. **Timeouts**: Ajuste timeouts se a performance mudar

### Debugging

Para debuggar testes que falharam:

1. Use `--debug` para execução passo a passo
2. Verifique screenshots em `test-results/`
3. Use `--headed` para ver o browser durante execução
4. Adicione `await page.pause()` para pausar em pontos específicos

## Considerações Especiais

### Dependências Externas

- **Backend API**: Testes assumem que o backend está funcionando
- **Dados**: Alguns testes podem falhar se não houver dados no sistema
- **Rede**: Conexão com internet necessária para ícones e fonts

### Limitações Conhecidas

1. Testes não criam dados de teste isolados
2. Dependem do estado atual do banco de dados
3. Alguns testes podem ser flaky devido a timing de rede

### Performance

Os testes são otimizados para:
- Execução paralela (2 workers)
- Reutilização de contexto quando possível
- Seletores eficientes
- Timeouts apropriados 