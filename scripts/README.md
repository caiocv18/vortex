# Scripts do Vortex

Este diretório contém scripts utilitários para automação de tarefas no projeto Vortex.

## Scripts Disponíveis

### 🧪 run-auth-registration-tests.sh
Script especializado para execução de testes do fluxo de criação de conta do serviço de autorização.

#### Funcionalidades
- **42 testes focados** no fluxo de criação de conta
- **Múltiplos modos de execução** com opções flexíveis
- **Saída colorizada** para melhor visualização
- **Relatórios automáticos** de cobertura e resultados
- **Integração CI/CD** com modo otimizado

### 🔐 run-auth-password-recovery-tests.sh
Script especializado para execução de testes do fluxo de recuperação de senha do serviço de autorização.

#### Funcionalidades ✅ 100% ESTÁVEIS
- **56 testes focados** no fluxo de recuperação de senha
- **4 categorias especializadas**: Service, Resource, Security, Validation
- **Testes de segurança** abrangentes contra vulnerabilidades
- **Cobertura completa** de DTOs e validações
- **Múltiplos modos de execução** com opções flexíveis
- **Correções implementadas**: Rate limiting, token invalidation, concurrent handling, timing attacks

#### Uso Básico - Registro
```bash
# Executar todos os testes (padrão)
./scripts/run-auth-registration-tests.sh

# Executar apenas testes unitários (32 testes)
./scripts/run-auth-registration-tests.sh --unit

# Executar apenas testes de integração (10 testes)
./scripts/run-auth-registration-tests.sh --integration
```

#### Uso Básico - Recuperação de Senha
```bash
# Executar todos os testes de password recovery (56 testes)
./scripts/run-auth-password-recovery-tests.sh --all

# Por categoria específica
./scripts/run-auth-password-recovery-tests.sh --unit        # Service tests (15)
./scripts/run-auth-password-recovery-tests.sh --integration # Resource tests (15)
./scripts/run-auth-password-recovery-tests.sh --security    # Security tests (8)
./scripts/run-auth-password-recovery-tests.sh --validation  # Validation tests (18)
```

#### Opções Avançadas - Ambos Scripts
```bash
# Modo verboso com detalhes completos
./scripts/run-auth-registration-tests.sh --verbose
./scripts/run-auth-password-recovery-tests.sh --verbose

# Modo rápido (pula testes lentos)
./scripts/run-auth-registration-tests.sh --quick
./scripts/run-auth-password-recovery-tests.sh --quick

# Gerar relatório de cobertura
./scripts/run-auth-registration-tests.sh --coverage
./scripts/run-auth-password-recovery-tests.sh --coverage

# Modo watch (re-executa em mudanças)
./scripts/run-auth-registration-tests.sh --watch
./scripts/run-auth-password-recovery-tests.sh --watch

# Modo CI (coverage + report + formato simplificado)
./scripts/run-auth-registration-tests.sh --ci
./scripts/run-auth-password-recovery-tests.sh --ci
```

#### Filtros e Configurações
```bash
# Filtrar testes por padrão
./scripts/run-auth-registration-tests.sh --filter "Password"

# Executar com perfil específico
./scripts/run-auth-registration-tests.sh --profile dev

# Gerar relatório HTML detalhado
./scripts/run-auth-registration-tests.sh --report
```

#### Cobertura de Testes

##### Registro de Usuário (42 testes)
- **SimplePasswordServiceTest**: 24 testes de validação e criptografia
- **ValidationTest**: 10 testes de validação de DTOs
- **SimpleAuthServiceTest**: 8 testes de estruturas e TestDataBuilder

##### Recuperação de Senha (56 testes)
- **PasswordRecoveryServiceTest**: 15 testes de lógica de negócio
- **PasswordRecoveryResourceTest**: 15 testes de endpoints REST
- **PasswordRecoverySecurityTest**: 8 testes de segurança e vulnerabilidades
- **PasswordRecoveryValidationTest**: 18 testes de validação de DTOs

**Total**: 98 testes cobrindo validação, criptografia, DTOs, estruturas de entidades, segurança e fluxos completos.

#### Saída de Exemplo
```
========================================
Vortex Auth Registration Test Runner
========================================
Configuration:
  Test Type: all
  Profile: test
  Verbose: false
  Coverage: false

========================================
Running All Tests
========================================
[INFO] Tests run: 42, Failures: 0, Errors: 0

✓ All Tests PASSED

========================================
Test Execution Complete
========================================
✓ All tests passed successfully!
```

#### Localização dos Relatórios
- **Testes**: `test-reports/auth-registration/`
- **Cobertura JaCoCo**: `coverage-reports/auth-registration/`
  - **HTML**: `index.html` (navegável no browser)
  - **XML**: `jacoco.xml` (integração CI/CD)
  - **CSV**: `jacoco.csv` (análise de dados)

#### Configuração JaCoCo
- **Plugin**: jacoco-maven-plugin 0.8.11
- **Quality Gates**: Mínimo 60% linhas, 50% branches
- **Exclusões**: DTOs, classes principais, test utilities
- **Integração**: Automática com o script via `--coverage`

---

### 🔍 test-queue-endpoint.sh
Script para testar endpoints de monitoramento de filas de mensagens.

#### Uso
```bash
./scripts/test-queue-endpoint.sh
```

#### Funcionalidade
- Testa conectividade com endpoints de filas
- Valida respostas da API de monitoramento
- Útil para debugging de messaging

---

### 🔗 check-ports.sh
Script para verificação de portas utilizadas pelos serviços.

#### Uso
```bash
./scripts/check-ports.sh              # pergunta antes de encerrar cada processo
./scripts/check-ports.sh --auto-kill  # encerra sem perguntar (o start-vortex.sh usa este)
```

#### Funcionalidade
- Verifica disponibilidade das portas padrão
- Só conta quem está escutando (LISTEN); conexões de cliente não são conflito
- Pula portas publicadas por containers Docker (quem cuida delas é o docker compose)
- Encerra com TERM, espera até 10 s e só então manda KILL

#### Portas Monitoradas
- **8080**: Vortex Application Service (Spring Boot)
- **8081**: Vortex Authorization Service (Quarkus)
- **5173**: Frontend Application Service (Vue.js)
- **3001**: Frontend Authorization Service (React)
- **9092**: Kafka
- **5432**: PostgreSQL

---

### 📊 medir-memoria.sh
Tabela de memória do Vortex em texto puro (o `start-vortex.sh` imprime no fim de toda subida).

#### Uso
```bash
./scripts/medir-memoria.sh
```

#### Funcionalidade
- RSS de cada processo iniciado pelo launcher (pid files em `logs/`), sozinho e com os filhos
- Uso e limite de memória de cada container `vortex-*` (`docker stats --no-stream`)
- Total da VM do WSL (`free -m`) e RAM livre do Windows (`powershell.exe` pela interop)
- Sempre sai com 0, mesmo com partes paradas

---

## Convenções de Scripts

### Estrutura Padrão
Todos os scripts seguem as seguintes convenções:

1. **Shebang**: `#!/bin/bash`
2. **Set options**: `set -e` (exit on error)
3. **Colors**: Definições de cores para saída
4. **Functions**: Funções auxiliares bem documentadas
5. **Help**: Função de ajuda com `--help`
6. **Error handling**: Tratamento adequado de erros

### Exemplo de Estrutura
```bash
#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Functions
print_color() {
    local color=$1
    shift
    echo -e "${color}$@${NC}"
}

show_help() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  --help    Show this help"
    exit 0
}

# Main logic
main() {
    # Implementation
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --help)
            show_help
            ;;
        *)
            echo "Unknown option: $1"
            show_help
            ;;
    esac
done

main "$@"
```

### Saída Colorizada
- **Verde**: Sucesso, operações bem-sucedidas
- **Amarelo**: Avisos, informações importantes
- **Vermelho**: Erros, falhas
- **Azul**: Informações gerais
- **Ciano**: Headers e separadores

### Códigos de Saída
- **0**: Sucesso
- **1**: Erro geral
- **2**: Uso incorreto (argumentos inválidos)
- **3**: Falha de dependência (serviços indisponíveis)

---

## Execução em CI/CD

### GitHub Actions
```yaml
- name: Run Auth Registration Tests
  run: ./scripts/run-auth-registration-tests.sh --ci

- name: Run Password Recovery Tests  
  run: ./scripts/run-auth-password-recovery-tests.sh --ci
  
- name: Check Ports
  run: ./scripts/check-ports.sh
  
- name: Test Queue Endpoints
  run: ./scripts/test-queue-endpoint.sh
```

### Jenkins Pipeline
```groovy
stage('Tests') {
    steps {
        sh './scripts/run-auth-registration-tests.sh --ci'
        sh './scripts/run-auth-password-recovery-tests.sh --ci'
        sh './scripts/check-ports.sh'
    }
}
```

---

## Troubleshooting

### Problema: Permission denied
```bash
# Solução: Dar permissão de execução
chmod +x scripts/*.sh
```

### Problema: Script não encontrado
```bash
# Solução: Executar do diretório raiz do projeto
cd /path/to/vortex
./scripts/script-name.sh
```

### Problema: Dependências não encontradas
```bash
# Solução: Verificar se Maven e Java estão instalados
java --version
mvn --version
```

### Problema: Portas em uso
```bash
# Solução: Verificar e parar serviços conflitantes
./scripts/check-ports.sh
./start-vortex.sh --stop
```

---

## Desenvolvimento de Novos Scripts

### Padrões a Seguir
1. **Nomenclatura**: `kebab-case.sh`
2. **Localização**: Diretório `/scripts/`
3. **Documentação**: Adicionar seção neste README
4. **Testes**: Incluir validação básica
5. **Logs**: Usar função `print_color` para saída consistente

### Template para Novo Script
```bash
#!/bin/bash
set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
SCRIPT_NAME="$(basename "$0")"
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"

print_color() {
    local color=$1
    shift
    echo -e "${color}$@${NC}"
}

print_header() {
    echo ""
    print_color "$CYAN" "========================================"
    print_color "$CYAN" "$1"
    print_color "$CYAN" "========================================"
    echo ""
}

show_help() {
    echo "Usage: $SCRIPT_NAME [options]"
    echo ""
    echo "Description: Your script description here"
    echo ""
    echo "Options:"
    echo "  --help              Show this help message"
    echo "  --verbose           Enable verbose output"
    echo ""
    echo "Examples:"
    echo "  $SCRIPT_NAME --verbose"
    exit 0
}

main() {
    print_header "Script Name"
    
    # Your implementation here
    
    print_color "$GREEN" "✓ Operation completed successfully!"
}

# Parse command line arguments
VERBOSE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            show_help
            ;;
        *)
            print_color "$RED" "Unknown option: $1"
            show_help
            ;;
    esac
done

main "$@"
```

---

## Referências

- [Bash Scripting Guide](https://tldp.org/LDP/abs/html/)
- [Shell Style Guide](https://google.github.io/styleguide/shellguide.html)
- [Testing Best Practices](https://martinfowler.com/articles/practical-test-pyramid.html)
- [CI/CD Integration](https://docs.github.com/en/actions)

---

*Última atualização: Scripts implementados para automação de testes - 42 testes de registro e 56 testes de recuperação de senha (100% estáveis).*