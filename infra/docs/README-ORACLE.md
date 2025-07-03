# Configuração Oracle para Produção

Este documento descreve como configurar e executar a aplicação com Oracle Database no profile de produção.

## Pré-requisitos

- Docker e Docker Compose instalados
- Acesso ao registro Oracle Container Registry (container-registry.oracle.com)

## Configuração Implementada

### 1. Profile de Produção (`application-prd.properties`)

O profile `prd` foi configurado com:
- Conexão Oracle JDBC usando variáveis de ambiente
- Dialect específico para Oracle 12c+
- Configurações otimizadas para produção
- Console H2 desabilitado

### 2. Scripts de Inicialização Oracle

Criados scripts em `oracle/init/`:
- `01-init-schema.sql`: Criação de usuário e sequências
- `02-insert-data.sql`: Inserção de dados iniciais

### 3. Docker Compose Atualizado

- Profile Spring definido como `prd`
- Volume para scripts de inicialização Oracle
- Variáveis de ambiente para conexão com banco

## Como Executar

### 1. Usando o script de conveniência (Recomendado)

```bash
cd backend
./start-oracle.sh -d
```

### 2. Subir o ambiente completo (Oracle + Aplicação)

```bash
cd backend
docker-compose up -d
```

### 2. Verificar logs da aplicação

```bash
docker logs vortex-app -f
```

### 3. Verificar logs do Oracle

```bash
docker logs vortex-db -f
```

### 4. Acessar a aplicação

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

## Configurações de Conexão

### Variáveis de Ambiente (já configuradas no docker-compose.yml)

- `SPRING_PROFILES_ACTIVE=prd`
- `DB_HOST=db`
- `DB_PORT=1521`
- `DB_SID=ORCLCDB`
- `DB_SERVICE=ORCLPDB1`
- `DB_USER=system`
- `DB_PASSWORD=Oracle_1234`

### String de Conexão Oracle

```
jdbc:oracle:thin:@db:1521:ORCLCDB
```

## Estrutura das Tabelas

As seguintes tabelas são criadas automaticamente pelo Hibernate:

1. **tipo_produto**
   - id (SEQUENCE: tipo_produto_seq)
   - nome

2. **produto**
   - id (SEQUENCE: produto_seq)
   - descricao
   - valor_fornecedor
   - quantidade_em_estoque
   - tipo_produto_id (FK)

3. **movimento_estoque**
   - id (SEQUENCE: movimento_estoque_seq)
   - data_movimento
   - tipo_movimentacao
   - quantidade_movimentada
   - valor_venda
   - produto_id (FK)

## Dados de Teste

Os scripts inserem automaticamente:
- 10 tipos de produto
- 20 produtos de exemplo
- 20 movimentos de estoque (10 entradas + 10 saídas)

## Troubleshooting

### Oracle não inicia

1. Verificar se o Docker tem recursos suficientes (mínimo 2GB RAM)
2. Aguardar inicialização completa (pode levar alguns minutos)
3. Verificar logs: `docker logs vortex-db`

### Aplicação não conecta ao Oracle

1. Verificar se o Oracle está healthy: `docker ps`
2. Verificar conectividade: `docker exec vortex-app ping db`
3. Verificar logs da aplicação: `docker logs vortex-app`

### Executar apenas Oracle (sem aplicação)

```bash
docker-compose up db -d
```

### Conectar ao Oracle externamente

- Host: localhost
- Porta: 1521
- SID: ORCLCDB
- PDB: ORCLPDB1
- Usuário: system
- Senha: Oracle_1234

## Desenvolvimento Local

Para executar a aplicação localmente (fora do Docker) conectando ao Oracle no Docker:

1. Subir apenas o Oracle:
```bash
docker-compose up db -d
```

2. Executar a aplicação com profile prd:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prd
```

Ou definir variáveis de ambiente:
```bash
export SPRING_PROFILES_ACTIVE=prd
export DB_HOST=localhost
mvn spring-boot:run
``` 